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
            @RequestParam(defaultValue = "asc") String direction) {
        return flatService.page(page, size, sortBy, direction);
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
    @GetMapping("/{flatId}") public FlatResponse get(@PathVariable UUID flatId) { return flatService.get(flatId); }
    @PostMapping public ResponseEntity<FlatResponse> create(@Valid @RequestBody CreateFlatRequest request) {
        FlatResponse flat = flatService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/flats/" + flat.flatId())).body(flat);
    }
    @PutMapping("/{flatId}") public FlatResponse update(@PathVariable UUID flatId,
            @Valid @RequestBody UpdateFlatRequest request) { return flatService.update(flatId, request); }
    @PatchMapping("/{flatId}/deactivate") public FlatResponse deactivate(@PathVariable UUID flatId) {
        return flatService.deactivate(flatId);
    }
}
