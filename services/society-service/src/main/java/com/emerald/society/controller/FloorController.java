package com.emerald.society.controller;

import com.emerald.society.dto.FloorResponse;
import com.emerald.society.service.FloorService;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/{floorNumber}")
    public FloorResponse getFloor(@PathVariable int floorNumber) {
        return floorService.getFloor(floorNumber);
    }
}
