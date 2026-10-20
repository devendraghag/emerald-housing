package com.emerald.society.controller;

import com.emerald.society.dto.CreateFlatRequest;
import com.emerald.society.dto.FlatResponse;
import com.emerald.society.dto.PageResponse;
import com.emerald.society.dto.UpdateFlatRequest;
import com.emerald.society.service.FlatService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flats")
public class FlatController {
    private final FlatService flatService;
    public FlatController(FlatService flatService) { this.flatService = flatService; }
    @GetMapping public List<FlatResponse> list() { return flatService.list(); }
    @GetMapping("/page") public PageResponse<FlatResponse> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "flatNumber") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(required=false) String search,@RequestParam(required=false) String status,@RequestParam(required=false) Integer floorNumber) {
        return flatService.searchPage(page, size, sortBy, direction,search,status,floorNumber);
    }
    @GetMapping("/cursor")
    public com.emerald.society.api.CursorPage<FlatResponse> cursor(@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String after,@RequestParam(required=false) String search,@RequestParam(required=false) String status,@RequestParam(required=false) Integer floorNumber) {
        return flatService.cursor(limit,after,search,status,floorNumber);
    }
    @GetMapping("/by-floor/{floorNumber}")
    public List<FlatResponse> byFloor(@PathVariable int floorNumber) { return flatService.listByFloorNumber(floorNumber); }
    @GetMapping("/by-floor/{floorNumber}/page")
    public PageResponse<FlatResponse> byFloorPage(@PathVariable int floorNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "flatNumber") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {
        return flatService.pageByFloorNumber(floorNumber, page, size, sortBy, direction);
    }
    @GetMapping("/{flatId}") public ResponseEntity<FlatResponse> get(@PathVariable UUID flatId, Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader(value="If-None-Match",required=false) String ifNoneMatch) {
        boolean memberValidation = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "SERVICE_MEMBER_FLAT_READ".equals(authority.getAuthority()));
        var body=memberValidation ? flatService.get(flatId) : flatService.getCached(flatId);
        return com.emerald.society.api.VersionPreconditions.read(body,com.emerald.society.api.VersionPreconditions.tag("flat",flatId,body.version()),ifNoneMatch);
    }
    @PostMapping public ResponseEntity<FlatResponse> create(@Valid @RequestBody CreateFlatRequest request) {
        FlatResponse flat = flatService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/flats/" + flat.flatId())).body(flat);
    }
    @PutMapping("/{flatId}") public ResponseEntity<FlatResponse> update(@PathVariable UUID flatId,
            @Valid @RequestBody UpdateFlatRequest request,@org.springframework.web.bind.annotation.RequestHeader(value="If-Match",required=false) String ifMatch) {
        var body=flatService.update(flatId,request,ifMatch);return ResponseEntity.ok().eTag(com.emerald.society.api.VersionPreconditions.tag("flat",flatId,body.version())).body(body);
    }
    @PatchMapping("/{flatId}/deactivate") public ResponseEntity<FlatResponse> deactivate(@PathVariable UUID flatId,@org.springframework.web.bind.annotation.RequestHeader(value="If-Match",required=false) String ifMatch) {
        var body=flatService.deactivate(flatId,ifMatch);return ResponseEntity.ok().eTag(com.emerald.society.api.VersionPreconditions.tag("flat",flatId,body.version())).body(body);
    }
}
