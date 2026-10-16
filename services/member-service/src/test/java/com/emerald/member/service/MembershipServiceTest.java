package com.emerald.member.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.emerald.member.client.SocietyClient;
import com.emerald.member.dto.CreateMembershipRequest;
import com.emerald.member.dto.MembershipResponse;
import com.emerald.member.dto.RelationshipType;
import com.emerald.member.dto.SocietyFlatResponse;
import com.emerald.member.exception.FlatNotFoundException;
import com.emerald.member.exception.MemberNotFoundException;
import com.emerald.member.exception.SocietyDependencyException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {
    @Mock MemberService members;
    @Mock SocietyClient society;
    @Mock MembershipWriter writer;
    @InjectMocks MembershipService service;

    @Test void validFlatIsCheckedBeforeMembershipIsPersisted() {
        UUID memberId = UUID.randomUUID(), flatId = UUID.randomUUID(), floorId = UUID.randomUUID();
        var request = new CreateMembershipRequest(flatId, RelationshipType.OWNER, LocalDate.now());
        var response = new MembershipResponse(UUID.randomUUID(), memberId, flatId, RelationshipType.OWNER, request.startDate(), null);
        when(society.getFlat(flatId)).thenReturn(new SocietyFlatResponse(flatId, "A-1", floorId, 1, "ACTIVE"));
        when(writer.persist(memberId, request)).thenReturn(response);
        assertSame(response, service.assign(memberId, request));
        verify(members).requireActive(memberId);
        verify(society).getFlat(flatId);
        verify(writer).persist(memberId, request);
    }

    @Test void nonexistentFlatIsNeverPersisted() {
        UUID memberId = UUID.randomUUID(), flatId = UUID.randomUUID();
        var request = new CreateMembershipRequest(flatId, RelationshipType.TENANT, LocalDate.now());
        when(society.getFlat(flatId)).thenThrow(new FlatNotFoundException(flatId));
        assertThrows(FlatNotFoundException.class, () -> service.assign(memberId, request));
        verifyNoInteractions(writer);
    }

    @Test void missingMemberStopsBeforeRemoteCall() {
        UUID memberId = UUID.randomUUID();
        var request = new CreateMembershipRequest(UUID.randomUUID(), RelationshipType.RESIDENT, LocalDate.now());
        doThrow(new MemberNotFoundException(memberId)).when(members).requireActive(memberId);
        assertThrows(MemberNotFoundException.class, () -> service.assign(memberId, request));
        verifyNoInteractions(society, writer);
    }

    @Test void societyOutageStopsBeforePersistence() {
        UUID memberId = UUID.randomUUID(), flatId = UUID.randomUUID();
        var request = new CreateMembershipRequest(flatId, RelationshipType.RESIDENT, LocalDate.now());
        when(society.getFlat(flatId)).thenThrow(new SocietyDependencyException(503,
                "SOCIETY_SERVICE_UNAVAILABLE", "Society Service is unavailable", null));
        assertThrows(SocietyDependencyException.class, () -> service.assign(memberId, request));
        verifyNoInteractions(writer);
    }
}
