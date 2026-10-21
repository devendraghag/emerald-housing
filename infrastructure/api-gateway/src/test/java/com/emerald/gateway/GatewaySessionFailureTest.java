package com.emerald.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.emerald.gateway.security.InternalSessionValidationController;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import reactor.core.publisher.Mono;

class GatewaySessionFailureTest {
    @Test void invalidInternalTokenIsRejectedBeforeSessionLookup() {
        var sessions = mock(ServerSecurityContextRepository.class);
        var controller = new InternalSessionValidationController(sessions, "x".repeat(32));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/auth/session"));
        assertEquals(403, controller.validate(exchange, "wrong-token").block(Duration.ofSeconds(2)).getStatusCode().value());
        verifyNoInteractions(sessions);
    }

    @Test void missingSessionIsUnauthorized() {
        var sessions = mock(ServerSecurityContextRepository.class);
        var controller = new InternalSessionValidationController(sessions, "x".repeat(32));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/auth/session"));
        when(sessions.load(exchange)).thenReturn(Mono.empty());
        assertEquals(401, controller.validate(exchange, "x".repeat(32)).block(Duration.ofSeconds(2)).getStatusCode().value());
    }
}
