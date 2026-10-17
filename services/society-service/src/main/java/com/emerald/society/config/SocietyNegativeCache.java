package com.emerald.society.config;

import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Short-lived negative entries for unknown admin flat lookups. */
@Component
public class SocietyNegativeCache {
    private static final String KEY_PREFIX = "society:negative:flat:";

    private final StringRedisTemplate redis;
    private final SocietyCacheMetrics metrics;
    private final Duration ttl;

    public SocietyNegativeCache(StringRedisTemplate redis, SocietyCacheMetrics metrics,
            @Value("${society.cache.ttl.not-found:30s}") Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Negative-cache TTL must be positive");
        }
        this.redis = redis;
        this.metrics = metrics;
        this.ttl = ttl;
    }

    public boolean containsFlat(UUID flatId) {
        if (metrics.shouldBypassRedis()) return false;
        try {
            boolean hit = Boolean.TRUE.equals(redis.hasKey(key(flatId)));
            metrics.recordNegativeLookup(hit ? "hit" : "miss");
            return hit;
        } catch (DataAccessException exception) {
            metrics.recordRedisFailure("negative-get", exception);
            return false;
        }
    }

    public void rememberMissingFlat(UUID flatId) {
        if (metrics.shouldBypassRedis()) return;
        try {
            redis.opsForValue().set(key(flatId), "1", ttl);
            metrics.recordNegativeLookup("write");
        } catch (DataAccessException exception) {
            metrics.recordRedisFailure("negative-put", exception);
        }
    }

    public void evictFlat(UUID flatId) {
        try {
            redis.delete(key(flatId));
        } catch (DataAccessException exception) {
            metrics.recordRedisFailure("negative-evict", exception);
        }
    }

    private static String key(UUID flatId) {
        return KEY_PREFIX + flatId;
    }
}
