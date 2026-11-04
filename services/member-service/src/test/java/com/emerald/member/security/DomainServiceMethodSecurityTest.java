package com.emerald.member.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.emerald.member.lostfound.LostFoundEventPublisher;
import com.emerald.member.lostfound.LostFoundPhotoStorage;
import com.emerald.member.lostfound.LostFoundService;
import com.emerald.member.notices.CommunityNoticeEventPublisher;
import com.emerald.member.notices.CommunityNoticeService;
import com.emerald.member.client.SocietyClient;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(DomainServiceMethodSecurityTest.Config.class)
class DomainServiceMethodSecurityTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean("memberAccess") MemberAccess memberAccess() { return new MemberAccess(); }
        @Bean CommunityNoticeService communityNoticeService() {
            return new CommunityNoticeService(mock(JdbcTemplate.class), mock(SocietyClient.class),
                    mock(CommunityNoticeEventPublisher.class));
        }
        @Bean LostFoundService lostFoundService() {
            return new LostFoundService(mock(JdbcTemplate.class), mock(LostFoundEventPublisher.class),
                    mock(LostFoundPhotoStorage.class));
        }
    }

    @Autowired private CommunityNoticeService notices;
    @Autowired private LostFoundService lostFound;

    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void privilegedServiceMethodsDenyCallersWithoutAdminGrants() {
        var resident = new SessionIdentityResponse(UUID.randomUUID(), "resident", "RESIDENT", "EMERALD",
                UUID.randomUUID(), Set.of(), Set.of("RESIDENT"), Set.of("COMMUNITY_NOTICE_BOARD:SOCIETY"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(resident, null, Set.of()));

        assertThrows(AccessDeniedException.class, notices::reviewQueue);
        assertThrows(AccessDeniedException.class, lostFound::adminReports);
    }
}
