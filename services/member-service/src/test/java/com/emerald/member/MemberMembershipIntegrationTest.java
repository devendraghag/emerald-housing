package com.emerald.member;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.emerald.member.client.SocietyClient;
import com.emerald.member.dto.CreateMemberRequest;
import com.emerald.member.dto.CreateMembershipRequest;
import com.emerald.member.dto.EndMembershipRequest;
import com.emerald.member.dto.RelationshipType;
import com.emerald.member.dto.SocietyFlatResponse;
import com.emerald.member.exception.FlatNotFoundException;
import com.emerald.member.exception.DuplicateMembershipException;
import com.emerald.member.service.MemberService;
import com.emerald.member.service.MembershipService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MemberMembershipIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("app.datasource.writer.jdbc-url", POSTGRES::getJdbcUrl);
        properties.add("app.datasource.writer.username", POSTGRES::getUsername);
        properties.add("app.datasource.writer.password", POSTGRES::getPassword);
    }
    @Autowired MemberService members;
    @Autowired MembershipService assignments;
    @MockitoBean SocietyClient society;

    @Test void savesMembershipOnlyAfterSocietyConfirmsFlat() {
        UUID flatId = UUID.randomUUID();
        var member = members.create(new CreateMemberRequest("Integration User", "9876543210", null));
        var request = new CreateMembershipRequest(flatId, RelationshipType.OWNER, LocalDate.now());
        when(society.getFlat(flatId)).thenReturn(new SocietyFlatResponse(flatId, "A-1", UUID.randomUUID(), 1, "ACTIVE"));
        assertEquals(flatId, assignments.assign(member.memberId(), request).flatId());
        assertEquals(1, assignments.byMember(member.memberId()).size());

        UUID missingFlat = UUID.randomUUID();
        var badRequest = new CreateMembershipRequest(missingFlat, RelationshipType.TENANT, LocalDate.now());
        when(society.getFlat(missingFlat)).thenThrow(new FlatNotFoundException(missingFlat));
        assertThrows(FlatNotFoundException.class, () -> assignments.assign(member.memberId(), badRequest));
        assertEquals(1, assignments.byMember(member.memberId()).size());
    }

    @Test void membershipCanBePagedAndEndedWithoutLosingItsHistory() {
        UUID flatId = UUID.randomUUID();
        var member = members.create(new CreateMemberRequest("Membership Lifecycle", "9876543210", null));
        var memberPage = members.getPage(0, 1, "createdAt", "desc");
        assertEquals(1, memberPage.totalElements());
        assertEquals(member.memberId(), memberPage.content().getFirst().memberId());
        var request = new CreateMembershipRequest(flatId, RelationshipType.RESIDENT, LocalDate.now());
        when(society.getFlat(flatId)).thenReturn(new SocietyFlatResponse(flatId, "A-2", UUID.randomUUID(), 1, "ACTIVE"));
        var created = assignments.assign(member.memberId(), request);
        assertThrows(DuplicateMembershipException.class, () -> assignments.assign(member.memberId(), request));

        var page = assignments.pageByMember(member.memberId(), 0, 5, "startDate", "desc");
        assertEquals(1, page.totalElements());
        assertEquals(created.membershipId(), page.content().getFirst().membershipId());

        var ended = assignments.end(member.memberId(), created.membershipId(), new EndMembershipRequest(LocalDate.now()));
        assertEquals(LocalDate.now(), ended.endDate());
        var repeated = assignments.end(member.memberId(), created.membershipId(), new EndMembershipRequest(LocalDate.now()));
        assertEquals(ended.endDate(), repeated.endDate());
        assertEquals(1, assignments.byMember(member.memberId()).size());
    }
}
