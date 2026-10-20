package com.emerald.society.repository;

import com.emerald.society.entity.FloorEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorRepository extends JpaRepository<FloorEntity, UUID> {
    @org.springframework.data.jpa.repository.Query("select f from FloorEntity f where f.society.societyCode = 'EMERALD' order by f.floorNumber")
    List<FloorEntity> findAllByOrderByFloorNumberAsc();
    @org.springframework.data.jpa.repository.Query("select f from FloorEntity f where f.society.societyCode = 'EMERALD' and f.floorNumber = :floorNumber")
    Optional<FloorEntity> findByFloorNumber(int floorNumber);
    long countBySocietySocietyCode(String societyCode);
}
