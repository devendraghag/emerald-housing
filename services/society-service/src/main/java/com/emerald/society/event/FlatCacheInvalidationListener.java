package com.emerald.society.event;

import com.emerald.society.config.SocietyCacheConfiguration;
import com.emerald.society.config.SocietyCacheMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FlatCacheInvalidationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlatCacheInvalidationListener.class);
    private final CacheManager caches;
    private final SocietyCacheMetrics metrics;

    public FlatCacheInvalidationListener(CacheManager cacheManager, SocietyCacheMetrics metrics) {
        this.caches = cacheManager;
        this.metrics = metrics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidateAfterCommit(FlatReferenceChangedEvent event) {
        evict(SocietyCacheConfiguration.FLAT_BY_ID, event.flatId().toString());
        evict(SocietyCacheConfiguration.ALL_FLATS, "all");
        evictFloor(event.previousFloorNumber());
        if (!java.util.Objects.equals(event.previousFloorNumber(), event.currentFloorNumber())) {
            evictFloor(event.currentFloorNumber());
        }
    }

    private void evictFloor(Integer floorNumber) {
        if (floorNumber != null) evict(SocietyCacheConfiguration.FLATS_BY_FLOOR, floorNumber.toString());
    }

    private void evict(String cacheName, String key) {
        try {
            Cache cache = caches.getCache(cacheName);
            if (cache == null) {
                LOGGER.error("Configured Society cache {} is missing", cacheName);
                return;
            }
            cache.evict(key);
        } catch (RuntimeException exception) {
            metrics.recordRedisFailure("evict", exception);
        }
    }
}
