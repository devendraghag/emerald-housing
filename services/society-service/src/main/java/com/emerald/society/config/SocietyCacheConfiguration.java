package com.emerald.society.config;

import com.emerald.society.dto.FlatResponse;
import com.emerald.society.dto.FloorResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableCaching
@EnableScheduling
public class SocietyCacheConfiguration implements CachingConfigurer {
    public static final String FLOOR_LIST = "societyFloors";
    public static final String FLAT_BY_ID = "societyFlatById";
    public static final String FLATS_BY_FLOOR = "societyFlatsByFloor";
    public static final String ALL_FLATS = "societyFlats";
    private static final String LOAD_LOCK_PREFIX = "society:cache:load-lock:";
    private static final DefaultRedisScript<Long> RELEASE_IF_OWNED = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final CacheErrorHandler cacheErrorHandler;
    private final ObjectProvider<CacheManager> cacheManagers;
    private final SocietyCacheMetrics cacheMetrics;
    private final StringRedisTemplate redis;
    private final Duration loadLockWait;
    private final Duration loadLockLease;
    private final Duration loadLockRetry;

    public SocietyCacheConfiguration(SocietyCacheMetrics cacheMetrics, ObjectProvider<CacheManager> cacheManagers,
            StringRedisTemplate redis,
            @Value("${society.cache.load-lock.wait:2s}") Duration loadLockWait,
            @Value("${society.cache.load-lock.lease:30s}") Duration loadLockLease,
            @Value("${society.cache.load-lock.retry:25ms}") Duration loadLockRetry) {
        this.cacheMetrics = cacheMetrics;
        this.cacheManagers = cacheManagers;
        this.redis = redis;
        if (loadLockWait.isNegative() || loadLockWait.isZero()
                || loadLockLease.isNegative() || loadLockLease.isZero()
                || loadLockRetry.isNegative() || loadLockRetry.isZero()
                || loadLockRetry.compareTo(loadLockWait) > 0) {
            throw new IllegalArgumentException("Cache load-lock durations must be positive and retry must not exceed wait");
        }
        this.loadLockWait = loadLockWait;
        this.loadLockLease = loadLockLease;
        this.loadLockRetry = loadLockRetry;
        this.cacheErrorHandler = new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                cacheMetrics.recordRedisFailure("get", exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                cacheMetrics.recordRedisFailure("put", exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                cacheMetrics.recordRedisFailure("evict", exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                cacheMetrics.recordRedisFailure("clear", exception);
            }
        };
    }

    @Bean(name = "cacheManager")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory, JsonMapper jsonMapper,
            @Value("${society.cache.ttl.floors:10m}") Duration floorTtl,
            @Value("${society.cache.ttl.flats-by-floor:5m}") Duration flatsByFloorTtl,
            @Value("${society.cache.ttl.flat-by-id:2m}") Duration flatByIdTtl,
            @Value("${society.cache.ttl.all-flats:5m}") Duration allFlatsTtl,
            @Value("${society.cache.ttl.jitter:30s}") Duration ttlJitter) {
        JavaType floorListType = jsonMapper.getTypeFactory().constructCollectionType(List.class, FloorResponse.class);
        JavaType flatListType = jsonMapper.getTypeFactory().constructCollectionType(List.class, FlatResponse.class);
        RedisCacheConfiguration defaults = cacheConfig(flatByIdTtl, ttlJitter,
                new JacksonJsonRedisSerializer<>(jsonMapper, FlatResponse.class));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withCacheConfiguration(FLOOR_LIST, cacheConfig(floorTtl, ttlJitter,
                        new JacksonJsonRedisSerializer<>(jsonMapper, floorListType)))
                .withCacheConfiguration(FLAT_BY_ID, cacheConfig(flatByIdTtl, ttlJitter,
                        new JacksonJsonRedisSerializer<>(jsonMapper, FlatResponse.class)))
                .withCacheConfiguration(FLATS_BY_FLOOR, cacheConfig(flatsByFloorTtl, ttlJitter,
                        new JacksonJsonRedisSerializer<>(jsonMapper, flatListType)))
                .withCacheConfiguration(ALL_FLATS, cacheConfig(allFlatsTtl, ttlJitter,
                        new JacksonJsonRedisSerializer<>(jsonMapper, flatListType)))
                .initialCacheNames(Set.of(FLOOR_LIST, FLAT_BY_ID, FLATS_BY_FLOOR, ALL_FLATS))
                .disableCreateOnMissingCache()
                .enableStatistics()
                .transactionAware()
                .build();
    }

    @Override
    public CacheResolver cacheResolver() {
        NoOpCacheManager bypassManager = new NoOpCacheManager();
        ConcurrentHashMap<String, Cache> instrumentedCaches = new ConcurrentHashMap<>();
        return (CacheOperationInvocationContext<?> context) -> {
            Collection<String> names = context.getOperation().getCacheNames();
            if (cacheMetrics.shouldBypassRedis()) {
                names.forEach(cacheMetrics::recordCacheBypass);
                return names.stream().map(bypassManager::getCache)
                        .filter(java.util.Objects::nonNull).toList();
            }
            CacheManager manager = cacheManagers.getObject();
            return names.stream().map(name -> instrumentedCaches.computeIfAbsent(name, cacheName -> {
                Cache cache = manager.getCache(cacheName);
                return cache == null ? null : new InstrumentedCache(cache, cacheMetrics, redis,
                        loadLockWait, loadLockLease, loadLockRetry);
            })).filter(java.util.Objects::nonNull).toList();
        };
    }

    private static final class InstrumentedCache implements Cache {
        private final Cache delegate;
        private final SocietyCacheMetrics metrics;
        private final StringRedisTemplate redis;
        private final Duration loadLockWait;
        private final Duration loadLockLease;
        private final Duration loadLockRetry;

        private InstrumentedCache(Cache delegate, SocietyCacheMetrics metrics, StringRedisTemplate redis,
                Duration loadLockWait, Duration loadLockLease, Duration loadLockRetry) {
            this.delegate = delegate;
            this.metrics = metrics;
            this.redis = redis;
            this.loadLockWait = loadLockWait;
            this.loadLockLease = loadLockLease;
            this.loadLockRetry = loadLockRetry;
        }

        @Override public String getName() { return delegate.getName(); }
        @Override public Object getNativeCache() { return delegate.getNativeCache(); }

        @Override
        public ValueWrapper get(Object key) {
            if (metrics.shouldBypassRedis()) return null;
            try {
                return metrics.recordCacheRead(getName(), key, () -> delegate.get(key));
            } catch (RuntimeException exception) {
                metrics.recordRedisFailure("get", exception);
                return null;
            }
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            if (metrics.shouldBypassRedis()) return null;
            try {
                return metrics.recordCacheRead(getName(), key, () -> delegate.get(key, type));
            } catch (RuntimeException exception) {
                metrics.recordRedisFailure("get", exception);
                return null;
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Object key, Callable<T> valueLoader) {
            ValueWrapper cached = get(key);
            if (cached != null) return (T) cached.get();
            if (metrics.shouldBypassRedis()) return load(valueLoader, key, false);

            String lockKey = LOAD_LOCK_PREFIX + getName() + ":" + key;
            String ownerToken = UUID.randomUUID().toString();
            long deadline = System.nanoTime() + loadLockWait.toNanos();
            boolean acquired = false;
            try {
                while (true) {
                    if (metrics.shouldBypassRedis()) return load(valueLoader, key, false);
                    try {
                        Boolean result = redis.opsForValue().setIfAbsent(lockKey, ownerToken, loadLockLease);
                        acquired = Boolean.TRUE.equals(result);
                    } catch (RuntimeException exception) {
                        metrics.recordRedisFailure("load-lock", exception);
                        return load(valueLoader, key, false);
                    }

                    if (acquired) {
                        cached = readAfterLock(key);
                        if (cached != null) return (T) cached.get();
                        return load(valueLoader, key, true);
                    }

                    cached = readAfterLock(key);
                    if (cached != null) return (T) cached.get();

                    long remainingNanos = deadline - System.nanoTime();
                    if (remainingNanos <= 0) {
                        metrics.recordLoadLockTimeout(getName());
                        return load(valueLoader, key, false);
                    }
                    TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, loadLockRetry.toNanos()));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return load(valueLoader, key, false);
            } finally {
                if (acquired) releaseLoadLock(lockKey, ownerToken);
            }
        }

        private void releaseLoadLock(String lockKey, String ownerToken) {
            try {
                redis.execute(RELEASE_IF_OWNED, java.util.List.of(lockKey), ownerToken);
            } catch (RuntimeException exception) {
                metrics.recordRedisFailure("load-lock-release", exception);
            }
        }

        private <T> T load(Callable<T> valueLoader, Object key, boolean cacheResult) {
            try {
                T loaded = valueLoader.call();
                if (cacheResult && loaded != null) put(key, loaded);
                return loaded;
            } catch (Exception exception) {
                throw new ValueRetrievalException(key, valueLoader, exception);
            }
        }

        @Override
        public void put(Object key, Object value) {
            if (metrics.shouldBypassRedis()) return;
            try {
                delegate.put(key, value);
            } catch (RuntimeException exception) {
                metrics.recordRedisFailure("put", exception);
            }
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            if (metrics.shouldBypassRedis()) return null;
            try {
                return delegate.putIfAbsent(key, value);
            } catch (RuntimeException exception) {
                metrics.recordRedisFailure("put", exception);
                return null;
            }
        }

        @Override
        public void evict(Object key) {
            try {
                delegate.evict(key);
            } catch (RuntimeException exception) {
                metrics.recordRedisFailure("evict", exception);
            }
        }

        @Override
        public boolean evictIfPresent(Object key) {
            try {
                return delegate.evictIfPresent(key);
            } catch (RuntimeException exception) {
                metrics.recordRedisFailure("evict", exception);
                return false;
            }
        }

        @Override
        public void clear() {
            try {
                delegate.clear();
            } catch (RuntimeException exception) {
                metrics.recordRedisFailure("clear", exception);
            }
        }

        private ValueWrapper readAfterLock(Object key) {
            if (metrics.shouldBypassRedis()) return null;
            try {
                return delegate.get(key);
            } catch (RuntimeException exception) {
                metrics.recordRedisFailure("get", exception);
                return null;
            }
        }

    }

    private static RedisCacheConfiguration cacheConfig(Duration ttl, Duration jitter,
            JacksonJsonRedisSerializer<?> valueSerializer) {
        if (ttl.isNegative() || ttl.isZero() || jitter.isNegative() || jitter.compareTo(ttl) >= 0) {
            throw new IllegalArgumentException("Cache TTL must be positive and jitter must be smaller than the TTL");
        }
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl((key, value) -> {
                    long jitterMillis = jitter.toMillis();
                    long reduction = jitterMillis == 0 ? 0 : ThreadLocalRandom.current().nextLong(jitterMillis + 1);
                    return Duration.ofMillis(ttl.toMillis() - reduction);
                })
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "society:cache:v2:" + cacheName + "::")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer));
    }

    @Override
    public CacheManager cacheManager() {
        return cacheManagers.getObject();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return cacheErrorHandler;
    }
}
