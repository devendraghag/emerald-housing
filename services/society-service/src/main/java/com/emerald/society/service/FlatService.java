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
    @Autowired private com.emerald.society.audit.AuditTrail audit;
    @Autowired private com.emerald.society.security.SocietyAccess access;
    @Autowired private com.emerald.society.api.DirectoryQueries queries;
    @Autowired private com.emerald.society.api.VersionPreconditions versions;
    private void audit(String action,UUID id,Object before,Object after) { if(audit!=null)audit.record(action,"FLAT",id,before,after); }
    private boolean visible(FlatResponse flat) { return access==null || !access.resident() || access.ownFlats().contains(flat.flatId()); }
    private void requireSociety(FlatEntity flat) { if(access!=null && !societies.findBySocietyCode("EMERALD").map(society->society.getId().equals(flat.getSocietyId())).orElse(false))throw new org.springframework.security.access.AccessDeniedException("Wrong society"); }
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
    @PreAuthorize("@societyAccess.flat(#p0) or hasAuthority('SERVICE_MEMBER_FLAT_READ')")
    public FlatResponse get(UUID id) {
        return loadFlat(id);
    }
    @Transactional(readOnly = true)
    @PreAuthorize("@societyAccess.flat(#p0)")
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
    @PreAuthorize("@societyAccess.read()")
    @Cacheable(cacheNames = SocietyCacheConfiguration.ALL_FLATS, key = "'all'", condition = "@societyAccess.staff()", sync = true)
    public List<FlatResponse> list() {
        return cacheMetrics.recordDatabaseLoad("all-flats", () -> flats.findAllResponsesByFlatNumberAsc().stream().filter(this::visible).toList());
    }
    @Transactional(readOnly = true)
    @PreAuthorize("@societyAccess.read()")
    @Cacheable(cacheNames = SocietyCacheConfiguration.FLATS_BY_FLOOR, key = "#p0.toString()", condition = "@societyAccess.staff()", sync = true)
    public List<FlatResponse> listByFloorNumber(int floorNumber) {
        return cacheMetrics.recordDatabaseLoad("flats-by-floor", () -> {
            var floor = floors.findByFloorNumber(floorNumber)
                    .orElseThrow(() -> new FloorEntityNotFoundException(floorNumber));
            return flats.findByFloorIdOrderByFlatNumberAsc(floor.getId()).stream()
                    .map(flat -> response(flat, floorNumber)).filter(this::visible).toList();
        });
    }
    @Transactional(readOnly = true)
    @PreAuthorize("@societyAccess.read()")
    public PageResponse<FlatResponse> page(int page, int size, String sortBy, String direction) {
        Pageable pageable = PageRequests.of(page, size, sortBy, direction, Set.of("flatNumber", "status", "createdAt", "updatedAt"));
        Page<FlatResponse> result = queries==null ? flats.findAllResponses(pageable) : queries.page(null,null,null,pageable).map(flat -> response(flat,flat.getFloor().getFloorNumber()));
        return PageResponse.from(result, flat -> flat, sortBy, direction);
    }
    @Transactional(readOnly = true)
    @PreAuthorize("@societyAccess.read()")
    public PageResponse<FlatResponse> pageByFloorNumber(int floorNumber, int page, int size, String sortBy, String direction) {
        var floor = floors.findByFloorNumber(floorNumber)
                .orElseThrow(() -> new FloorEntityNotFoundException(floorNumber));
        Pageable pageable = PageRequests.of(page, size, sortBy, direction, Set.of("flatNumber", "status", "createdAt", "updatedAt"));
        return PageResponse.from(queries==null ? flats.findByFloorId(floor.getId(), pageable) : queries.page(null,null,floorNumber,pageable),
                flat -> response(flat, floorNumber), sortBy, direction);
    }
    @Transactional(readOnly=true) @PreAuthorize("@societyAccess.read()")
    public PageResponse<FlatResponse> searchPage(int page,int size,String sortBy,String direction,String search,String status,Integer floorNumber) {
        var pageable=PageRequests.of(page,size,sortBy,direction,Set.of("flatNumber","status","createdAt","updatedAt"));
        return PageResponse.from(queries.page(search,status,floorNumber,pageable),flat->response(flat,flat.getFloor().getFloorNumber()),sortBy,direction);
    }
    @Transactional(readOnly=true) @PreAuthorize("@societyAccess.read()")
    public com.emerald.society.api.CursorPage<FlatResponse> cursor(int limit,String after,String search,String status,Integer floorNumber) {
        var result=queries.cursor(search,status,floorNumber,limit,after);
        return new com.emerald.society.api.CursorPage<>(result.items().stream().map(flat->response(flat,flat.getFloor().getFloorNumber())).toList(),result.nextCursor(),result.hasNext());
    }
    @Transactional
    @PreAuthorize("@societyAccess.has('FLAT_CREATE','SOCIETY')")
    public FlatResponse create(CreateFlatRequest request) {
        var floor = floors.findByFloorNumber(request.floorNumber())
                .orElseThrow(() -> new FloorEntityNotFoundException(request.floorNumber()));
        var society = societies.findBySocietyCode("EMERALD").orElseThrow();
        Instant now = Instant.now();
        var entity = new FlatEntity(UUID.randomUUID(), society.getId(), floor.getId(),
                request.flatNumber().trim(), "ACTIVE", now);
        entity.setFloorReference(floor);
        var flat = flats.saveAndFlush(entity);
        events.publishEvent(new FlatReferenceChangedEvent(flat.getId(), null, floor.getFloorNumber()));
        var result=response(flat, floor.getFloorNumber());audit("CREATE",flat.getId(),null,result);return result;
    }
    @Transactional
    @PreAuthorize("@societyAccess.has('FLAT_UPDATE','SOCIETY')")
    public FlatResponse update(UUID id, UpdateFlatRequest request) { return update(id,request,null); }
    @Transactional @PreAuthorize("@societyAccess.has('FLAT_UPDATE','SOCIETY')")
    public FlatResponse update(UUID id, UpdateFlatRequest request,String ifMatch) {
        var flat = flats.findById(id).orElseThrow(() -> new FlatNotFoundException(id));
        requireSociety(flat);
        if(versions!=null) versions.check(ifMatch,"flat",id,flat.getVersion());
        var before=responseWithFloor(flat);
        int previousFloorNumber = floors.findById(flat.getFloorId())
                .orElseThrow(() -> new IllegalStateException("Flat floor is missing")).getFloorNumber();
        var floor = floors.findByFloorNumber(request.floorNumber())
                .orElseThrow(() -> new FloorEntityNotFoundException(request.floorNumber()));
        flat.update(floor.getId(), request.flatNumber().trim(), Instant.now());
        flat.setFloorReference(floor);
        FlatEntity updated = flats.saveAndFlush(flat);
        events.publishEvent(new FlatReferenceChangedEvent(id, previousFloorNumber, floor.getFloorNumber()));
        var result=response(updated, floor.getFloorNumber());audit("UPDATE",id,before,result);return result;
    }
    @Transactional
    @PreAuthorize("@societyAccess.has('FLAT_DEACTIVATE','SOCIETY')")
    public FlatResponse deactivate(UUID id) { return deactivate(id,null); }
    @Transactional @PreAuthorize("@societyAccess.has('FLAT_DEACTIVATE','SOCIETY')")
    public FlatResponse deactivate(UUID id,String ifMatch) {
        var flat = flats.findById(id).orElseThrow(() -> new FlatNotFoundException(id));
        requireSociety(flat);
        if(versions!=null) versions.check(ifMatch,"flat",id,flat.getVersion());
        var before=responseWithFloor(flat);
        int previousFloorNumber = floors.findById(flat.getFloorId())
                .orElseThrow(() -> new IllegalStateException("Flat floor is missing")).getFloorNumber();
        boolean changed=flat.deactivate(Instant.now());
        FlatEntity updated = flats.saveAndFlush(flat);
        events.publishEvent(new FlatReferenceChangedEvent(id, previousFloorNumber, previousFloorNumber));
        var result=responseWithFloor(updated);if(changed)audit("DEACTIVATE",id,before,result);return result;
    }
    private FlatResponse loadFlat(UUID id) {
        return cacheMetrics.recordDatabaseLoad("flat-by-id", () -> {
            var flat = flats.findById(id).orElseThrow(() -> new FlatNotFoundException(id));
            requireSociety(flat);
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
        return new FlatResponse(flat.getId(), flat.getFlatNumber(), flat.getFloorId(), floorNumber, flat.getStatus(), flat.getVersion());
    }
}
