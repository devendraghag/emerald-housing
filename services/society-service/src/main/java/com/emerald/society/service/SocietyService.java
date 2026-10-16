package com.emerald.society.service;

import com.emerald.society.dto.SocietyResponse;
import com.emerald.society.repository.FloorRepository;
import com.emerald.society.repository.SocietyRepository;
import org.springframework.stereotype.Service;

@Service
public class SocietyService {
    private final SocietyRepository societyRepository;
    private final FloorRepository floorRepository;
    public SocietyService(SocietyRepository societyRepository, FloorRepository floorRepository) {
        this.societyRepository = societyRepository;
        this.floorRepository = floorRepository;
    }
    public SocietyResponse getSociety() {
        var society = societyRepository.findBySocietyCode("EMERALD")
                .orElseThrow(() -> new IllegalStateException("Configured society is missing"));
        return new SocietyResponse(society.getSocietyCode(), society.getName(), Math.toIntExact(floorRepository.count()));
    }
}
