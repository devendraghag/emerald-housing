package com.emerald.society.service;

import com.emerald.society.dto.FloorResponse;
import com.emerald.society.exception.FloorNotFoundException;
import com.emerald.society.repository.FloorRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
public class FloorService {
    private final FloorRepository floorRepository;
    public FloorService(FloorRepository floorRepository) { this.floorRepository = floorRepository; }
    @PreAuthorize("hasRole('ADMIN')")
    public List<FloorResponse> getFloors() {
        return floorRepository.findAllByOrderByFloorNumberAsc().stream()
                .map(floor -> new FloorResponse(floor.getFloorNumber())).toList();
    }
    @PreAuthorize("hasRole('ADMIN')")
    public FloorResponse getFloor(int floorNumber) {
        return floorRepository.findByFloorNumber(floorNumber)
                .map(floor -> new FloorResponse(floor.getFloorNumber()))
                .orElseThrow(() -> new FloorNotFoundException(floorNumber));
    }
}
