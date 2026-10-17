package com.emerald.society.config;

import com.emerald.society.dto.FloorResponse;
import com.emerald.society.repository.FloorRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** Warms the small floor reference list so the first admin request can use Redis. */
@Component
public class SocietyCacheWarmup implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocietyCacheWarmup.class);

    private final CacheManager caches;
    private final FloorRepository floors;
    private final SocietyCacheMetrics metrics;
    private final boolean enabled;

    public SocietyCacheWarmup(CacheManager caches, FloorRepository floors, SocietyCacheMetrics metrics,
            @Value("${society.cache.warmup.enabled:true}") boolean enabled) {
        this.caches = caches;
        this.floors = floors;
        this.metrics = metrics;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            metrics.recordCacheWarmup(SocietyCacheConfiguration.FLOOR_LIST, "disabled");
            return;
        }
        try {
            Cache cache = caches.getCache(SocietyCacheConfiguration.FLOOR_LIST);
            if (cache == null) throw new IllegalStateException("Society floor cache is not configured");
            if (cache.get("all") == null) {
                List<FloorResponse> values = floors.findAllByOrderByFloorNumberAsc().stream()
                        .map(floor -> new FloorResponse(floor.getFloorNumber())).toList();
                cache.put("all", values);
                metrics.recordCacheWarmup(SocietyCacheConfiguration.FLOOR_LIST, "loaded");
            } else {
                metrics.recordCacheWarmup(SocietyCacheConfiguration.FLOOR_LIST, "already-warm");
            }
        } catch (DataAccessException | IllegalStateException exception) {
            metrics.recordCacheWarmup(SocietyCacheConfiguration.FLOOR_LIST, "failed");
            metrics.recordRedisFailure("warmup", exception);
            LOGGER.warn("Society floor cache warmup was skipped; the service will load floors on demand", exception);
        }
    }
}
