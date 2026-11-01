package com.emerald.society.repository;

import com.emerald.society.entity.SocietyEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SocietyRepository extends JpaRepository<SocietyEntity, UUID> {
    Optional<SocietyEntity> findBySocietyCode(String societyCode);
    @Modifying
    @Transactional
    @Query(value="UPDATE societies SET name = :name, updated_at = CURRENT_TIMESTAMP WHERE society_code = :code",nativeQuery=true)
    int updateName(@Param("code") String code, @Param("name") String name);
}
