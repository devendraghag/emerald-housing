package com.emerald.society.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SocietyCacheMetrics {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocietyCacheMetrics.class);
    private static final long REDIS_BYPASS_NANOS = java.time.Duration.ofSeconds(5).toNanos();
    private final MeterRegistry meters;
    private final AtomicLong bypassUntilNanos = new AtomicLong();

    public SocietyCacheMetrics(MeterRegistry meters) {
        this.meters = meters;
    }

    public <T> T recordDatabaseLoad(String lookup, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meters);
        meters.counter("society.reference.database.loads", "lookup", lookup).increment();
        try {
            return operation.get();
        } finally {
            sample.stop(Timer.builder("society.reference.database.load")
                    .description("Time to load Society reference data from PostgreSQL")
                    .tag("lookup", lookup)
                    .register(meters));
        }
    }

    public <T> T recordCacheRead(String cacheName, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meters);
        String outcome = "error";
        T result;
        try {
            result = operation.get();
            outcome = result == null ? "miss" : "hit";
            meters.counter("society.cache.lookups", "cache", cacheName, "outcome", outcome).increment();
            return result;
        } finally {
            sample.stop(Timer.builder("society.cache.lookup")
                    .description("Time spent reading a Society cache entry")
                    .tag("cache", cacheName)
                    .tag("outcome", outcome)
                    .register(meters));
        }
    }

    public void recordCacheBypass(String cacheName) {
        meters.counter("society.cache.lookups", "cache", cacheName, "outcome", "redis-bypass").increment();
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
