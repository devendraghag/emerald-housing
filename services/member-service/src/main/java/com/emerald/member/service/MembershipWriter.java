package com.emerald.member.service;

import com.emerald.member.dto.CreateMembershipRequest;
import com.emerald.member.dto.MembershipResponse;
import com.emerald.member.dto.PageResponse;
import com.emerald.member.dto.EndMembershipRequest;
import com.emerald.member.entity.FlatMembershipEntity;
import com.emerald.member.exception.DuplicateMembershipException;
import com.emerald.member.exception.MemberNotFoundException;
import com.emerald.member.exception.InactiveMemberException;
import com.emerald.member.exception.InvalidMembershipEndDateException;
import com.emerald.member.exception.MembershipNotFoundException;
import com.emerald.member.config.PageRequests;
import com.emerald.member.service.MemberStatus;
import com.emerald.member.repository.FlatMembershipRepository;
import com.emerald.member.repository.MemberRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipWriter {
    @org.springframework.beans.factory.annotation.Autowired private com.emerald.member.audit.AuditTrail audit;
    @org.springframework.beans.factory.annotation.Autowired private com.emerald.member.security.MemberAccess access;
    private final FlatMembershipRepository memberships;
    private final MemberRepository members;
    public MembershipWriter(FlatMembershipRepository memberships, MemberRepository members) { this.memberships=memberships; this.members=members; }
    @Transactional
    public MembershipResponse persist(UUID memberId, CreateMembershipRequest request) {
        var member = members.findByIdForUpdate(memberId).orElseThrow(() -> new MemberNotFoundException(memberId));
        if (member.getStatus() != MemberStatus.ACTIVE) throw new InactiveMemberException();
        String relationship = request.relationshipType().name();
        if (memberships.existsByMemberIdAndFlatIdAndRelationshipTypeAndEndDateIsNull(memberId, request.flatId(), relationship))
            throw new DuplicateMembershipException();
        Instant now = Instant.now();
        try {
            FlatMembershipEntity saved = memberships.saveAndFlush(new FlatMembershipEntity(UUID.randomUUID(), memberId,
                    request.flatId(), relationship, request.startDate(), now));
            var result=response(saved);if(audit!=null)audit.record("ASSIGN","MEMBERSHIP",saved.getId(),null,result);return result;
        } catch (DataIntegrityViolationException ex) { throw new DuplicateMembershipException(); }
    }
    @Transactional(readOnly = true)
    public List<MembershipResponse> byMember(UUID memberId) {
        if (!members.existsById(memberId)) throw new MemberNotFoundException(memberId);
        return memberships.findAllByMemberIdOrderByStartDateDesc(memberId).stream().map(MembershipWriter::response).toList();
    }
    @Transactional(readOnly = true)
    public List<MembershipResponse> byFlat(UUID flatId) {
        return memberships.findAllByFlatIdOrderByStartDateDesc(flatId).stream().filter(row->access==null || !access.resident() || row.getMemberId().equals(access.ownMember())).map(MembershipWriter::response).toList();
    }
    @Transactional(readOnly = true)
    public PageResponse<MembershipResponse> pageByMember(UUID memberId, int page, int size, String sortBy, String direction) {
        if (!members.existsById(memberId)) throw new MemberNotFoundException(memberId);
        Pageable pageable = PageRequests.of(page, size, sortBy, direction,
                Set.of("startDate", "endDate", "createdAt", "updatedAt", "relationshipType"));
        Page<FlatMembershipEntity> result = memberships.findAllByMemberId(memberId, pageable);
        return PageResponse.from(result, MembershipWriter::response, sortBy, direction);
    }
    @Transactional(readOnly = true)
    public PageResponse<MembershipResponse> pageByFlat(UUID flatId, int page, int size, String sortBy, String direction) {
        Pageable pageable = PageRequests.of(page, size, sortBy, direction,
                Set.of("startDate", "endDate", "createdAt", "updatedAt", "relationshipType"));
        Page<FlatMembershipEntity> result = access!=null && access.resident() ? memberships.findAllByFlatIdAndMemberId(flatId,access.ownMember(),pageable) : memberships.findAllByFlatId(flatId, pageable);
        return PageResponse.from(result, MembershipWriter::response, sortBy, direction);
    }
    @Transactional
    public MembershipResponse end(UUID memberId, UUID membershipId, EndMembershipRequest request) {
        members.findByIdForUpdate(memberId).orElseThrow(() -> new MemberNotFoundException(memberId));
        FlatMembershipEntity membership = memberships.findByIdAndMemberId(membershipId, memberId)
                .orElseThrow(() -> new MembershipNotFoundException(membershipId));
        if (request.endDate().isBefore(membership.getStartDate())) throw new InvalidMembershipEndDateException();
        if (membership.getEndDate() == null) {
            var before=response(membership);
            membership.end(request.endDate(), Instant.now());
            memberships.saveAndFlush(membership);
            if(audit!=null)audit.record("END","MEMBERSHIP",membership.getId(),before,response(membership));
        }
        return response(membership);
    }
    private static MembershipResponse response(FlatMembershipEntity m) {
        return new MembershipResponse(m.getId(), m.getMemberId(), m.getFlatId(),
                com.emerald.member.dto.RelationshipType.valueOf(m.getRelationshipType()), m.getStartDate(), m.getEndDate());
    }
}
