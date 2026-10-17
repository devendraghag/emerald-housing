package com.emerald.society.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SocietyCacheMetrics {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocietyCacheMetrics.class);
    private static final long REDIS_BYPASS_NANOS = java.time.Duration.ofSeconds(5).toNanos();
    private static final int MAX_TRACKED_CACHE_KEYS = 256;
    private final MeterRegistry meters;
    private final Semaphore fallbackLoadPermits;
    private final AtomicLong bypassUntilNanos = new AtomicLong();
    private final ConcurrentMap<String, LongAdder> keyAccesses = new ConcurrentHashMap<>();

    @Autowired
    public SocietyCacheMetrics(MeterRegistry meters,
            @Value("${society.cache.redis-fallback.max-concurrent-loads:16}") int maxConcurrentFallbackLoads) {
        if (maxConcurrentFallbackLoads < 1) {
            throw new IllegalArgumentException("Maximum concurrent cache-fallback loads must be positive");
        }
        this.meters = meters;
        this.fallbackLoadPermits = new Semaphore(maxConcurrentFallbackLoads);
    }

    public SocietyCacheMetrics(MeterRegistry meters) {
        this(meters, 16);
    }

    public <T> T recordDatabaseLoad(String lookup, Supplier<T> operation) {
        boolean fallbackLoad = shouldBypassRedis();
        if (fallbackLoad && !fallbackLoadPermits.tryAcquire()) {
            meters.counter("society.cache.redis.fallback.rejected", "lookup", lookup).increment();
            throw new CacheFallbackOverloadedException();
        }
        Timer.Sample sample = Timer.start(meters);
        meters.counter("society.reference.database.loads", "lookup", lookup).increment();
        try {
            return operation.get();
        } finally {
            if (fallbackLoad) fallbackLoadPermits.release();
            sample.stop(Timer.builder("society.reference.database.load")
                    .description("Time to load Society reference data from PostgreSQL")
                    .tag("lookup", lookup)
                    .register(meters));
        }
    }

    public <T> T recordCacheRead(String cacheName, Object key, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meters);
        String outcome = "error";
        T result;
        try {
            result = operation.get();
            outcome = result == null ? "miss" : "hit";
            meters.counter("society.cache.lookups", "cache", cacheName, "outcome", outcome).increment();
            recordCacheKeyAccess(cacheName, key);
            return result;
        } finally {
            sample.stop(Timer.builder("society.cache.lookup")
                    .description("Time spent reading a Society cache entry")
                    .tag("cache", cacheName)
                    .tag("outcome", outcome)
                    .register(meters));
        }
    }

    private void recordCacheKeyAccess(String cacheName, Object key) {
        String hash = Integer.toHexString(String.valueOf(key).hashCode());
        String id = cacheName + ":" + hash;
        LongAdder accesses = keyAccesses.get(id);
        if (accesses == null) {
            synchronized (keyAccesses) {
                accesses = keyAccesses.get(id);
                if (accesses == null && keyAccesses.size() < MAX_TRACKED_CACHE_KEYS) {
                    accesses = new LongAdder();
                    keyAccesses.put(id, accesses);
                    io.micrometer.core.instrument.Gauge.builder("society.cache.key.accesses", accesses,
                                    LongAdder::sum)
                            .description("Cache key accesses tracked locally by this Society Pod")
                            .tag("cache", cacheName)
                            .tag("key_hash", hash)
                            .register(meters);
                }
            }
        }
        if (accesses != null) accesses.increment();
    }

    public void recordCacheBypass(String cacheName) {
        meters.counter("society.cache.lookups", "cache", cacheName, "outcome", "redis-bypass").increment();
    }

    public void recordNegativeLookup(String outcome) {
        meters.counter("society.cache.negative.lookups", "outcome", outcome).increment();
    }

    public void recordLoadLockTimeout(String cacheName) {
        meters.counter("society.cache.load-lock.timeouts", "cache", cacheName).increment();
    }

    public void recordCacheWarmup(String cacheName, String outcome) {
        meters.counter("society.cache.warmups", "cache", cacheName, "outcome", outcome).increment();
    }

    public void recordRedisFailure(String operation, RuntimeException exception) {
        bypassUntilNanos.set(System.nanoTime() + REDIS_BYPASS_NANOS);
        meters.counter("society.cache.redis.failures", "operation", operation).increment();
        LOGGER.warn("Society cache Redis {} failed; continuing with PostgreSQL or bounded TTL behavior", operation,
                exception);
    }

    public boolean shouldBypassRedis() {
        return System.nanoTime() < bypassUntilNanos.get();
    }
}
