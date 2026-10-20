package com.emerald.society.repository;

import com.emerald.society.dto.FlatResponse;
import com.emerald.society.entity.FlatEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FlatRepository extends JpaRepository<FlatEntity, UUID> {
    List<FlatEntity> findAllByOrderByFlatNumberAsc();
    List<FlatEntity> findByFloorIdOrderByFlatNumberAsc(UUID floorId);
    Page<FlatEntity> findByFloorId(UUID floorId, Pageable pageable);

    @Query("""
            select new com.emerald.society.dto.FlatResponse(
                f.id, f.flatNumber, f.floorId, flr.floorNumber, f.status)
            from FlatEntity f join f.floor flr
            order by f.flatNumber asc
            """)
    List<FlatResponse> findAllResponsesByFlatNumberAsc();

    @Query(value = """
            select new com.emerald.society.dto.FlatResponse(
                f.id, f.flatNumber, f.floorId, flr.floorNumber, f.status)
            from FlatEntity f join f.floor flr
            """,
            countQuery = "select count(f) from FlatEntity f")
    Page<FlatResponse> findAllResponses(Pageable pageable);
}
