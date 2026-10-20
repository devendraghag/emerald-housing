package com.emerald.society.service;

import com.emerald.society.dto.FloorResponse;
import com.emerald.society.exception.FloorNotFoundException;
import com.emerald.society.repository.FloorRepository;
import com.emerald.society.config.SocietyCacheConfiguration;
import com.emerald.society.config.SocietyCacheMetrics;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FloorService {
    private final FloorRepository floorRepository;
    private final SocietyCacheMetrics cacheMetrics;
    @Autowired
    public FloorService(FloorRepository floorRepository, SocietyCacheMetrics cacheMetrics) {
        this.floorRepository = floorRepository;
        this.cacheMetrics = cacheMetrics;
    }
    public FloorService(FloorRepository floorRepository) {
        this(floorRepository, new SocietyCacheMetrics(new SimpleMeterRegistry()));
    }
    @PreAuthorize("@societyAccess.read()")
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = SocietyCacheConfiguration.FLOOR_LIST, key = "'all'", sync = true)
    public List<FloorResponse> getFloors() {
        return cacheMetrics.recordDatabaseLoad("floors", () -> floorRepository.findAllByOrderByFloorNumberAsc().stream()
                .map(floor -> new FloorResponse(floor.getFloorNumber())).toList());
    }
    @PreAuthorize("@societyAccess.read()")
    @Transactional(readOnly = true)
    public FloorResponse getFloor(int floorNumber) {
        return floorRepository.findByFloorNumber(floorNumber)
                .map(floor -> new FloorResponse(floor.getFloorNumber()))
                .orElseThrow(() -> new FloorNotFoundException(floorNumber));
    }
}
