package com.emerald.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.emerald.member.dto.*;
import com.emerald.member.entity.*;
import com.emerald.member.exception.*;
import com.emerald.member.repository.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MembershipWriterTest {
    private final FlatMembershipRepository memberships = mock(FlatMembershipRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final MembershipWriter writer = new MembershipWriter(memberships, members);
    private final UUID memberId = UUID.randomUUID(), flatId = UUID.randomUUID(), membershipId = UUID.randomUUID();
    private final LocalDate start = LocalDate.of(2026, 10, 1);
    private final CreateMembershipRequest request = new CreateMembershipRequest(flatId, RelationshipType.OWNER, start);
    private MemberEntity member;

    @BeforeEach void setUp() {
        member = new MemberEntity(memberId, "Asha", "9876543210", null, MemberStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH);
        when(members.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));
    }

    @Test void inactiveMemberIsRecheckedInsideWriter() {
        member.deactivate(Instant.now());
        assertThrows(InactiveMemberException.class, () -> writer.persist(memberId, request));
        verifyNoInteractions(memberships);
    }

    @Test void duplicateAssignmentDoesNotWrite() {
        when(memberships.existsByMemberIdAndFlatIdAndRelationshipTypeAndEndDateIsNull(memberId, flatId, "OWNER")).thenReturn(true);
        assertThrows(DuplicateMembershipException.class, () -> writer.persist(memberId, request));
        verify(memberships, never()).saveAndFlush(any());
    }

    @Test void concurrentDuplicateIsTranslated() {
        when(memberships.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertThrows(DuplicateMembershipException.class, () -> writer.persist(memberId, request));
    }

    @Test void endDateBeforeStartDoesNotMutateOrSave() {
        var row = existing();
        assertThrows(InvalidMembershipEndDateException.class, () -> writer.end(memberId, membershipId, new EndMembershipRequest(start.minusDays(1))));
        assertNull(row.getEndDate());
        verify(memberships, never()).saveAndFlush(any());
    }

    @Test void membershipMayEndOnStartDate() {
        var row = existing();
        assertEquals(start, writer.end(memberId, membershipId, new EndMembershipRequest(start)).endDate());
        verify(memberships).saveAndFlush(row);
    }

    @Test void repeatedEndPreservesOriginalHistory() {
        var row = existing();
        row.end(start.plusDays(2), Instant.now());
        assertEquals(start.plusDays(2), writer.end(memberId, membershipId, new EndMembershipRequest(start.plusDays(3))).endDate());
        verify(memberships, never()).saveAndFlush(any());
    }

    @Test void membershipBelongingToAnotherMemberCannotBeEnded() {
        assertThrows(MembershipNotFoundException.class, () -> writer.end(memberId, membershipId, new EndMembershipRequest(start)));
        verify(memberships, never()).saveAndFlush(any());
    }

    private FlatMembershipEntity existing() {
        var row = new FlatMembershipEntity(membershipId, memberId, flatId, "OWNER", start, Instant.EPOCH);
        when(memberships.findByIdAndMemberId(membershipId, memberId)).thenReturn(Optional.of(row));
        return row;
    }
}
