package com.emerald.society.repository;

import com.emerald.society.entity.SocietyEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocietyRepository extends JpaRepository<SocietyEntity, UUID> {
    Optional<SocietyEntity> findBySocietyCode(String societyCode);
}
