package com.emerald.society.service;

import com.emerald.society.dto.SocietyResponse;
import com.emerald.society.repository.FloorRepository;
import com.emerald.society.repository.SocietyRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocietyService {
    private final SocietyRepository societyRepository;
    private final FloorRepository floorRepository;
    public SocietyService(SocietyRepository societyRepository, FloorRepository floorRepository) {
        this.societyRepository = societyRepository;
        this.floorRepository = floorRepository;
    }
    @PreAuthorize("@societyAccess.has('SOCIETY_VIEW','SOCIETY')")
    public SocietyResponse getSociety() {
        var society = societyRepository.findBySocietyCode("EMERALD")
                .orElseThrow(() -> new IllegalStateException("Configured society is missing"));
        return new SocietyResponse(society.getSocietyCode(), society.getName(), Math.toIntExact(floorRepository.countBySocietySocietyCode("EMERALD")));
    }
    @Transactional
    @PreAuthorize("@societyAccess.has('SOCIETY_UPDATE','SOCIETY')")
    public SocietyResponse updateName(String name) {
        String normalized=name.trim().replaceAll("\\s+", " ");
        if(societyRepository.updateName("EMERALD",normalized)!=1)throw new IllegalStateException("Configured society is missing");
        return getSociety();
    }
}
