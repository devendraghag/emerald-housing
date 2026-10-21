package com.emerald.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.emerald.member.dto.CreateMemberRequest;
import com.emerald.member.entity.MemberEntity;
import com.emerald.member.exception.*;
import com.emerald.member.repository.MemberRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

class MemberServiceFailureTest {
    private final MemberRepository members = mock(MemberRepository.class);
    private final MemberService service = new MemberService(members);
    private final CreateMemberRequest request = new CreateMemberRequest("Asha", "9876543210", null);

    @Test void duplicateMobileIsRejectedBeforeSaving() {
        when(members.existsByMobileNumberAndStatus(request.mobileNumber(), MemberStatus.ACTIVE)).thenReturn(true);
        assertThrows(DuplicateMobileNumberException.class, () -> service.create(request));
        verify(members, never()).saveAndFlush(any());
    }

    @Test void concurrentMobileConflictIsTranslated() {
        when(members.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertThrows(DuplicateMobileNumberException.class, () -> service.create(request));
    }

    @Test void missingMemberIsRejected() {
        assertThrows(MemberNotFoundException.class, () -> service.requireActive(UUID.randomUUID()));
        verify(members, never()).saveAndFlush(any());
    }

    @Test void inactiveMemberCannotReceiveMemberships() {
        var id = UUID.randomUUID();
        var now = Instant.parse("2026-10-01T00:00:00Z");
        when(members.findById(id)).thenReturn(Optional.of(new MemberEntity(id, "Asha", "9876543210", null, MemberStatus.INACTIVE, now, now)));
        assertThrows(InactiveMemberException.class, () -> service.requireActive(id));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"contains space", "line\nbreak", "unsafe/key"})
    void invalidIdempotencyKeysDoNotTouchPersistence(String key) {
        assertThrows(InvalidIdempotencyKeyException.class, () -> service.createIdempotent(request, key, UUID.randomUUID()));
        verifyNoInteractions(members);
    }

    @Test void overlongIdempotencyKeyIsRejected() {
        assertThrows(InvalidIdempotencyKeyException.class, () -> service.createIdempotent(request, "a".repeat(129), UUID.randomUUID()));
        verifyNoInteractions(members);
    }
}
