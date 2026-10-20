package com.emerald.member.repository;

import com.emerald.member.entity.FlatMembershipEntity;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlatMembershipRepository extends JpaRepository<FlatMembershipEntity, UUID> {
    Optional<FlatMembershipEntity> findByIdAndMemberId(UUID id, UUID memberId);
    boolean existsByMemberIdAndFlatIdAndRelationshipTypeAndEndDateIsNull(UUID memberId, UUID flatId, String relationshipType);
    List<FlatMembershipEntity> findAllByMemberIdOrderByStartDateDesc(UUID memberId);
    List<FlatMembershipEntity> findAllByFlatIdOrderByStartDateDesc(UUID flatId);
    Page<FlatMembershipEntity> findAllByMemberId(UUID memberId, Pageable pageable);
    Page<FlatMembershipEntity> findAllByFlatId(UUID flatId, Pageable pageable);
    Page<FlatMembershipEntity> findAllByFlatIdAndMemberId(UUID flatId,UUID memberId,Pageable pageable);
}
