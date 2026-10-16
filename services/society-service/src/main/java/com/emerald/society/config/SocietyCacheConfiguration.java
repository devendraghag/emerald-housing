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
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.CacheResolver;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
public class SocietyCacheConfiguration implements CachingConfigurer {
    public static final String FLOOR_LIST = "societyFloors";
    public static final String FLAT_BY_ID = "societyFlatById";
    public static final String FLATS_BY_FLOOR = "societyFlatsByFloor";
    public static final String ALL_FLATS = "societyFlats";

    private final CacheErrorHandler cacheErrorHandler;
    private final ObjectProvider<CacheManager> cacheManagers;
    private final SocietyCacheMetrics cacheMetrics;

    public SocietyCacheConfiguration(SocietyCacheMetrics cacheMetrics, ObjectProvider<CacheManager> cacheManagers) {
        this.cacheMetrics = cacheMetrics;
        this.cacheManagers = cacheManagers;
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
            @Value("${society.cache.ttl.all-flats:5m}") Duration allFlatsTtl) {
        JavaType floorListType = jsonMapper.getTypeFactory().constructCollectionType(List.class, FloorResponse.class);
        JavaType flatListType = jsonMapper.getTypeFactory().constructCollectionType(List.class, FlatResponse.class);
        RedisCacheConfiguration defaults = cacheConfig(flatByIdTtl,
                new JacksonJsonRedisSerializer<>(jsonMapper, FlatResponse.class));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withCacheConfiguration(FLOOR_LIST, cacheConfig(floorTtl,
                        new JacksonJsonRedisSerializer<>(jsonMapper, floorListType)))
                .withCacheConfiguration(FLAT_BY_ID, cacheConfig(flatByIdTtl,
                        new JacksonJsonRedisSerializer<>(jsonMapper, FlatResponse.class)))
                .withCacheConfiguration(FLATS_BY_FLOOR, cacheConfig(flatsByFloorTtl,
                        new JacksonJsonRedisSerializer<>(jsonMapper, flatListType)))
                .withCacheConfiguration(ALL_FLATS, cacheConfig(allFlatsTtl,
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
                return cache == null ? null : new InstrumentedCache(cache, cacheMetrics);
            })).filter(java.util.Objects::nonNull).toList();
        };
    }

    private static final class InstrumentedCache implements Cache {
        private static final ReentrantLock[] LOAD_LOCKS = createLoadLocks();
        private final Cache delegate;
        private final SocietyCacheMetrics metrics;

        private InstrumentedCache(Cache delegate, SocietyCacheMetrics metrics) {
            this.delegate = delegate;
            this.metrics = metrics;
        }

        @Override public String getName() { return delegate.getName(); }
        @Override public Object getNativeCache() { return delegate.getNativeCache(); }

        @Override
        public ValueWrapper get(Object key) {
            if (metrics.shouldBypassRedis()) return null;
            try {
                return metrics.recordCacheRead(getName(), () -> delegate.get(key));
            } catch (RuntimeException exception) {
                metrics.recordRedisFailure("get", exception);
                return null;
            }
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            if (metrics.shouldBypassRedis()) return null;
            try {
                return metrics.recordCacheRead(getName(), () -> delegate.get(key, type));
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
            ReentrantLock lock = LOAD_LOCKS[Math.floorMod((getName() + key).hashCode(), LOAD_LOCKS.length)];
            boolean locked;
            try {
                locked = lock.tryLock(10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return load(valueLoader, key, false);
            }
            if (!locked) return load(valueLoader, key, false);
            try {
                cached = readAfterLock(key);
                if (cached != null) return (T) cached.get();
                return load(valueLoader, key, true);
            } finally {
                lock.unlock();
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

        private static ReentrantLock[] createLoadLocks() {
            ReentrantLock[] locks = new ReentrantLock[64];
            java.util.Arrays.setAll(locks, index -> new ReentrantLock());
            return locks;
        }
    }

    private static RedisCacheConfiguration cacheConfig(Duration ttl,
            JacksonJsonRedisSerializer<?> valueSerializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "society:cache:" + cacheName + "::")
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
