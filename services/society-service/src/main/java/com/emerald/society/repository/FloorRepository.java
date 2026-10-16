package com.emerald.society.repository;

import com.emerald.society.entity.FloorEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorRepository extends JpaRepository<FloorEntity, UUID> {
    List<FloorEntity> findAllByOrderByFloorNumberAsc();
    Optional<FloorEntity> findByFloorNumber(int floorNumber);
}
