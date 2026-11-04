package com.emerald.society.controller;

import com.emerald.society.dto.FloorResponse;
import com.emerald.society.dto.PageResponse;
import com.emerald.society.service.FloorService;
import com.emerald.society.dto.FloorRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/floors")
public class FloorController {

    private final FloorService floorService;

    public FloorController(FloorService floorService) {
        this.floorService = floorService;
    }

    @GetMapping
    public List<FloorResponse> getFloors() {
        return floorService.getFloors();
    }

    @GetMapping("/page")
    public PageResponse<FloorResponse> getFloorsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "floorNumber") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {
        return floorService.getFloorsPage(page, size, sortBy, direction);
    }

    @GetMapping("/{floorNumber}")
    public FloorResponse getFloor(@PathVariable int floorNumber) {
        return floorService.getFloor(floorNumber);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FloorResponse createFloor(@Valid @RequestBody FloorRequest request) {
        return floorService.create(request.floorNumber());
    }

    @PutMapping("/{floorNumber}")
    public FloorResponse updateFloor(@PathVariable int floorNumber,@Valid @RequestBody FloorRequest request) {
        return floorService.update(floorNumber,request.floorNumber());
    }
}
