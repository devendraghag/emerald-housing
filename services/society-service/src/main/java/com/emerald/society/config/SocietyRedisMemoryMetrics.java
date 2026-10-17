package com.emerald.society.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Samples Redis memory usage without exposing Redis keys or credential values. */
@Component
public class SocietyRedisMemoryMetrics {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocietyRedisMemoryMetrics.class);

    private final RedisConnectionFactory connections;
    private final Counter scrapeErrors;
    private final AtomicLong usedBytes = new AtomicLong();
    private final AtomicLong maxBytes = new AtomicLong();

    public SocietyRedisMemoryMetrics(RedisConnectionFactory connections, MeterRegistry meters) {
        this.connections = connections;
        io.micrometer.core.instrument.Gauge.builder("society.cache.redis.memory.used", usedBytes,
                        AtomicLong::get)
                .description("Redis memory used by the Society cache instance")
                .baseUnit("bytes")
                .register(meters);
        io.micrometer.core.instrument.Gauge.builder("society.cache.redis.memory.max", maxBytes,
                        AtomicLong::get)
                .description("Configured Redis max memory for the Society cache instance")
                .baseUnit("bytes")
                .register(meters);
        io.micrometer.core.instrument.Gauge.builder("society.cache.redis.memory.used.ratio",
                        () -> maxBytes.get() == 0 ? 0.0 : (double) usedBytes.get() / maxBytes.get())
                .description("Fraction of configured Redis max memory currently used")
                .register(meters);
        this.scrapeErrors = Counter.builder("society.cache.redis.memory.scrape.errors")
                .description("Failed Redis memory metric samples").register(meters);
    }

    @Scheduled(fixedDelayString = "${society.cache.redis.memory-scrape-interval:30s}")
    public void sampleMemory() {
        RedisConnection connection = null;
        try {
            connection = connections.getConnection();
            Properties info = connection.serverCommands().info("memory");
            if (info == null) return;
            usedBytes.set(parse(info.getProperty("used_memory"), usedBytes.get()));
            maxBytes.set(parse(info.getProperty("maxmemory"), maxBytes.get()));
        } catch (DataAccessException exception) {
            LOGGER.debug("Could not sample Society cache Redis memory", exception);
            // The cache error handler owns request fallback; a metrics scrape must not trigger it.
            scrapeErrors.increment();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (DataAccessException exception) {
                    scrapeErrors.increment();
                }
            }
        }
    }

    private static long parse(String value, long previous) {
        if (value == null) return previous;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return previous;
        }
    }
}
