package com.emerald.society.repository;

import com.emerald.society.entity.FlatEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlatRepository extends JpaRepository<FlatEntity, UUID> {
    List<FlatEntity> findAllByOrderByFlatNumberAsc();
    List<FlatEntity> findByFloorIdOrderByFlatNumberAsc(UUID floorId);
    Page<FlatEntity> findByFloorId(UUID floorId, Pageable pageable);
}
