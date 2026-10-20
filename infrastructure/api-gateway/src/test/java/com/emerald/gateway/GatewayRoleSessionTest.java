package com.emerald.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.emerald.gateway.security.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

class GatewayRoleSessionTest {
    @Test
    void refreshesRoleAndObjectScopeInsteadOfTrustingTheLoginSnapshot() {
        var id = UUID.randomUUID();
        var member = UUID.randomUUID();
        var flat = UUID.randomUUID();
        var sessions = mock(ServerSecurityContextRepository.class);
        var accounts = mock(AdminAuthenticationManager.class);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/auth/session"));
        var principal = new AdminIdentity(id, "account");
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of());
        when(sessions.load(exchange)).thenReturn(Mono.just(new SecurityContextImpl(authentication)));
        when(accounts.current(id)).thenReturn(Mono.just(new AdminIdentityResponse(id, "account", "RESIDENT", "EMERALD", member, Set.of(flat))));
        var controller = new InternalSessionValidationController(sessions, "x".repeat(32));
        ReflectionTestUtils.setField(controller, "accounts", accounts);

        var response = controller.validate(exchange, "x".repeat(32)).block();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        var identity = (SessionValidationResponse) response.getBody();
        assertEquals("RESIDENT", identity.role());
        assertEquals(member, identity.memberId());
        assertEquals(Set.of(flat), identity.flatIds());
    }

    @Test
    void accountServiceFailureFailsClosed() {
        var sessions = mock(ServerSecurityContextRepository.class);
        var accounts = mock(AdminAuthenticationManager.class);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/auth/session"));
        var id = UUID.randomUUID();
        when(sessions.load(exchange)).thenReturn(Mono.just(new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated(new AdminIdentity(id, "account"), null, java.util.List.of()))));
        when(accounts.current(id)).thenReturn(Mono.error(new IllegalStateException("unavailable")));
        var controller = new InternalSessionValidationController(sessions, "x".repeat(32));
        ReflectionTestUtils.setField(controller, "accounts", accounts);
        assertEquals(503, controller.validate(exchange, "x".repeat(32)).block().getStatusCode().value());
    }
}
