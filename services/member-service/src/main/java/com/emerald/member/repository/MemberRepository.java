package com.emerald.member.repository;

import com.emerald.member.entity.MemberEntity;
import com.emerald.member.service.MemberStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<MemberEntity, UUID> {
    boolean existsByMobileNumberAndStatus(String mobileNumber, MemberStatus status);
    Optional<MemberEntity> findByIdAndStatus(UUID id, MemberStatus status);
    List<MemberEntity> findAllByOrderByCreatedAtDesc();
    Page<MemberEntity> findAll(Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MemberEntity m where m.id = :id")
    Optional<MemberEntity> findByIdForUpdate(@Param("id") UUID id);
}
