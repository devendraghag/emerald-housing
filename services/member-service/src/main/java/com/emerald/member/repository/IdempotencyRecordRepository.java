package com.emerald.member.repository;

import com.emerald.member.entity.IdempotencyRecordEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {
    Optional<IdempotencyRecordEntity> findByActorIdAndOperationAndIdempotencyKey(
            UUID actorId, String operation, String idempotencyKey);

    @Modifying
    @Query("delete from IdempotencyRecordEntity record where record.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
