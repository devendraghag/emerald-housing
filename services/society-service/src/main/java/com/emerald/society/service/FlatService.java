package com.emerald.society.service;

import com.emerald.society.dto.CreateFlatRequest;
import com.emerald.society.dto.FlatResponse;
import com.emerald.society.dto.PageResponse;
import com.emerald.society.dto.UpdateFlatRequest;
import com.emerald.society.config.PageRequests;
import com.emerald.society.config.SocietyCacheConfiguration;
import com.emerald.society.config.SocietyCacheMetrics;
import com.emerald.society.config.SocietyNegativeCache;
import com.emerald.society.entity.FlatEntity;
import com.emerald.society.event.FlatReferenceChangedEvent;
import com.emerald.society.exception.FlatNotFoundException;
import com.emerald.society.exception.FloorEntityNotFoundException;
import com.emerald.society.repository.FlatRepository;
import com.emerald.society.repository.FloorRepository;
import com.emerald.society.repository.SocietyRepository;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.Set;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
public class FlatService {
    private final FlatRepository flats;
    private final FloorRepository floors;
    private final SocietyRepository societies;
    private final ApplicationEventPublisher events;
    private final SocietyCacheMetrics cacheMetrics;
    private final SocietyNegativeCache negativeCache;
    @Autowired
    public FlatService(FlatRepository flats, FloorRepository floors, SocietyRepository societies,
            ApplicationEventPublisher events, SocietyCacheMetrics cacheMetrics, SocietyNegativeCache negativeCache) {
        this.flats = flats; this.floors = floors; this.societies = societies;
        this.events = events; this.cacheMetrics = cacheMetrics; this.negativeCache = negativeCache;
    }
    public FlatService(FlatRepository flats, FloorRepository floors, SocietyRepository societies) {
        this(flats, floors, societies, event -> { }, new SocietyCacheMetrics(new SimpleMeterRegistry()), null);
    }
    // Member assignment validation must read the primary to avoid replica-lag false negatives.
    @Transactional(readOnly = false)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SERVICE_MEMBER_FLAT_READ')")
    public FlatResponse get(UUID id) {
        return loadFlat(id);
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    @Cacheable(cacheNames = SocietyCacheConfiguration.FLAT_BY_ID, key = "#p0.toString()", sync = true)
    public FlatResponse getCached(UUID id) {
        if (negativeCache != null && negativeCache.containsFlat(id)) {
            throw new FlatNotFoundException(id);
        }
        try {
            return cacheMetrics.recordDatabaseLoad("flat-by-id", () -> loadFlat(id));
        } catch (FlatNotFoundException exception) {
            if (negativeCache != null) negativeCache.rememberMissingFlat(id);
            throw exception;
        }
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    @Cacheable(cacheNames = SocietyCacheConfiguration.ALL_FLATS, key = "'all'", sync = true)
    public List<FlatResponse> list() {
        return cacheMetrics.recordDatabaseLoad("all-flats", () -> flats.findAllByOrderByFlatNumberAsc().stream()
                .map(this::responseWithFloor).toList());
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    @Cacheable(cacheNames = SocietyCacheConfiguration.FLATS_BY_FLOOR, key = "#p0.toString()", sync = true)
    public List<FlatResponse> listByFloorNumber(int floorNumber) {
        return cacheMetrics.recordDatabaseLoad("flats-by-floor", () -> {
            var floor = floors.findByFloorNumber(floorNumber)
                    .orElseThrow(() -> new FloorEntityNotFoundException(floorNumber));
            return flats.findByFloorIdOrderByFlatNumberAsc(floor.getId()).stream()
                    .map(flat -> response(flat, floorNumber)).toList();
        });
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<FlatResponse> page(int page, int size, String sortBy, String direction) {
        Pageable pageable = PageRequests.of(page, size, sortBy, direction, Set.of("flatNumber", "status", "createdAt", "updatedAt"));
        Page<FlatEntity> result = flats.findAll(pageable);
        return PageResponse.from(result, this::responseWithFloor, sortBy, direction);
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<FlatResponse> pageByFloorNumber(int floorNumber, int page, int size, String sortBy, String direction) {
        var floor = floors.findByFloorNumber(floorNumber)
                .orElseThrow(() -> new FloorEntityNotFoundException(floorNumber));
        Pageable pageable = PageRequests.of(page, size, sortBy, direction, Set.of("flatNumber", "status", "createdAt", "updatedAt"));
        return PageResponse.from(flats.findByFloorId(floor.getId(), pageable),
                flat -> response(flat, floorNumber), sortBy, direction);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public FlatResponse create(CreateFlatRequest request) {
        var floor = floors.findByFloorNumber(request.floorNumber())
                .orElseThrow(() -> new FloorEntityNotFoundException(request.floorNumber()));
        var society = societies.findBySocietyCode("EMERALD").orElseThrow();
        Instant now = Instant.now();
        var flat = flats.saveAndFlush(new FlatEntity(UUID.randomUUID(), society.getId(), floor.getId(),
                request.flatNumber().trim(), "ACTIVE", now));
        events.publishEvent(new FlatReferenceChangedEvent(flat.getId(), null, floor.getFloorNumber()));
        return response(flat, floor.getFloorNumber());
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public FlatResponse update(UUID id, UpdateFlatRequest request) {
        var flat = flats.findById(id).orElseThrow(() -> new FlatNotFoundException(id));
        int previousFloorNumber = floors.findById(flat.getFloorId())
                .orElseThrow(() -> new IllegalStateException("Flat floor is missing")).getFloorNumber();
        var floor = floors.findByFloorNumber(request.floorNumber())
                .orElseThrow(() -> new FloorEntityNotFoundException(request.floorNumber()));
        flat.update(floor.getId(), request.flatNumber().trim(), Instant.now());
        FlatEntity updated = flats.saveAndFlush(flat);
        events.publishEvent(new FlatReferenceChangedEvent(id, previousFloorNumber, floor.getFloorNumber()));
        return response(updated, floor.getFloorNumber());
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public FlatResponse deactivate(UUID id) {
        var flat = flats.findById(id).orElseThrow(() -> new FlatNotFoundException(id));
        int previousFloorNumber = floors.findById(flat.getFloorId())
                .orElseThrow(() -> new IllegalStateException("Flat floor is missing")).getFloorNumber();
        flat.deactivate(Instant.now());
        FlatEntity updated = flats.saveAndFlush(flat);
        events.publishEvent(new FlatReferenceChangedEvent(id, previousFloorNumber, previousFloorNumber));
        return responseWithFloor(updated);
    }
    private FlatResponse loadFlat(UUID id) {
        return cacheMetrics.recordDatabaseLoad("flat-by-id", () -> {
            var flat = flats.findById(id).orElseThrow(() -> new FlatNotFoundException(id));
            var floor = floors.findById(flat.getFloorId())
                    .orElseThrow(() -> new IllegalStateException("Flat floor is missing"));
            return response(flat, floor.getFloorNumber());
        });
    }
    private FlatResponse responseWithFloor(FlatEntity flat) {
        var floor = floors.findById(flat.getFloorId()).orElseThrow();
        return response(flat, floor.getFloorNumber());
    }
    private static FlatResponse response(FlatEntity flat, int floorNumber) {
        return new FlatResponse(flat.getId(), flat.getFlatNumber(), flat.getFloorId(), floorNumber, flat.getStatus());
    }
}
