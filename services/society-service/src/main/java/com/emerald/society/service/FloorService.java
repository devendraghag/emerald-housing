package com.emerald.society.service;

import com.emerald.society.dto.FloorResponse;
import com.emerald.society.exception.FloorNotFoundException;
import com.emerald.society.repository.FloorRepository;
import com.emerald.society.repository.SocietyRepository;
import com.emerald.society.config.SocietyCacheConfiguration;
import com.emerald.society.config.SocietyCacheMetrics;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FloorService {
    private final FloorRepository floorRepository;
    private final SocietyRepository societyRepository;
    private final SocietyCacheMetrics cacheMetrics;
    @Autowired
    public FloorService(FloorRepository floorRepository, SocietyRepository societyRepository, SocietyCacheMetrics cacheMetrics) {
        this.floorRepository = floorRepository;
        this.societyRepository = societyRepository;
        this.cacheMetrics = cacheMetrics;
    }
    public FloorService(FloorRepository floorRepository, SocietyCacheMetrics cacheMetrics) {
        this(floorRepository,null,cacheMetrics);
    }
    public FloorService(FloorRepository floorRepository) {
        this(floorRepository,null, new SocietyCacheMetrics(new SimpleMeterRegistry()));
    }
    @PreAuthorize("@societyAccess.has('SOCIETY_VIEW','SOCIETY')")
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = SocietyCacheConfiguration.FLOOR_LIST, key = "'all'", sync = true)
    public List<FloorResponse> getFloors() {
        return cacheMetrics.recordDatabaseLoad("floors", () -> floorRepository.findAllByOrderByFloorNumberAsc().stream()
                .map(floor -> new FloorResponse(floor.getFloorNumber())).toList());
    }
    @PreAuthorize("@societyAccess.has('SOCIETY_VIEW','SOCIETY')")
    @Transactional(readOnly = true)
    public FloorResponse getFloor(int floorNumber) {
        return floorRepository.findByFloorNumber(floorNumber)
                .map(floor -> new FloorResponse(floor.getFloorNumber()))
                .orElseThrow(() -> new FloorNotFoundException(floorNumber));
    }
    @PreAuthorize("@societyAccess.has('FLOOR_CREATE','SOCIETY')")
    @Transactional
    @CacheEvict(cacheNames={SocietyCacheConfiguration.FLOOR_LIST,SocietyCacheConfiguration.FLATS_BY_FLOOR},allEntries=true)
    public FloorResponse create(int floorNumber) {
        if(floorRepository.existsBySocietySocietyCodeAndFloorNumber("EMERALD",floorNumber))
            throw new com.emerald.society.api.ApiFailure(409,"DUPLICATE_FLOOR","Floor number already exists");
        var society=societyRepository.findBySocietyCode("EMERALD").orElseThrow(()->new IllegalStateException("Configured society is missing"));
        return new FloorResponse(floorRepository.saveAndFlush(new com.emerald.society.entity.FloorEntity(java.util.UUID.randomUUID(),society,floorNumber)).getFloorNumber());
    }
    @PreAuthorize("@societyAccess.has('FLOOR_UPDATE','SOCIETY')")
    @Transactional
    @CacheEvict(cacheNames={SocietyCacheConfiguration.FLOOR_LIST,SocietyCacheConfiguration.FLATS_BY_FLOOR,SocietyCacheConfiguration.ALL_FLATS,SocietyCacheConfiguration.FLAT_BY_ID},allEntries=true)
    public FloorResponse update(int currentNumber,int nextNumber) {
        var floor=floorRepository.findByFloorNumber(currentNumber).orElseThrow(()->new FloorNotFoundException(currentNumber));
        if(currentNumber!=nextNumber&&floorRepository.existsBySocietySocietyCodeAndFloorNumber("EMERALD",nextNumber))
            throw new com.emerald.society.api.ApiFailure(409,"DUPLICATE_FLOOR","Floor number already exists");
        floor.changeFloorNumber(nextNumber);
        return new FloorResponse(floorRepository.saveAndFlush(floor).getFloorNumber());
    }
}
