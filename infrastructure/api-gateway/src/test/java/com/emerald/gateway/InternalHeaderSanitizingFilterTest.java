package com.emerald.gateway;

import static org.junit.jupiter.api.Assertions.*;
import com.emerald.gateway.security.InternalHeaderSanitizingFilter;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class InternalHeaderSanitizingFilterTest {
    @Test void stripsSpoofedIdentityAndServiceTokensButPreservesPublicHeaders() {
        var names = List.of("X-Gateway-Authentication-Token", "X-Internal-Session-Validation-Token", "X-Member-Service-Token", "X-Authenticated-User", "X-Authenticated-Role", "X-User", "X-Role");
        var request = MockServerHttpRequest.get("/api/members").header("X-Correlation-ID", "trace-123").header("Cookie", "SESSION=abc");
        names.forEach(name -> request.header(name.toLowerCase(java.util.Locale.ROOT), "spoofed", "second-value"));
        var forwarded = new AtomicReference<ServerWebExchange>();
        new InternalHeaderSanitizingFilter().filter(MockServerWebExchange.from(request), exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        }).block(Duration.ofSeconds(2));
        assertNotNull(forwarded.get());
        names.forEach(name -> assertFalse(forwarded.get().getRequest().getHeaders().containsHeader(name), name));
        assertEquals("trace-123", forwarded.get().getRequest().getHeaders().getFirst("X-Correlation-ID"));
        assertEquals("SESSION=abc", forwarded.get().getRequest().getHeaders().getFirst("Cookie"));
    }
}
