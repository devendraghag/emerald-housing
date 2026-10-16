package com.emerald.gateway.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal/auth/session")
public class InternalSessionValidationController {
    private final ServerSecurityContextRepository securityContexts;
    private final byte[] expectedToken;

    public InternalSessionValidationController(ServerSecurityContextRepository securityContexts,
            @Value("${emerald.security.session-validation-token:}") String expectedToken) {
        this.securityContexts = securityContexts;
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping
    public Mono<ResponseEntity<?>> validate(ServerWebExchange exchange,
            @RequestHeader(name = "X-Internal-Session-Validation-Token", required = false) String token) {
        if (expectedToken.length < 32 || token == null || !MessageDigest.isEqual(expectedToken,
                token.getBytes(StandardCharsets.UTF_8))) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(exchange,
                    HttpStatus.FORBIDDEN, "INTERNAL_CALL_FORBIDDEN", "Internal validation is not permitted")));
        }
        Mono<ResponseEntity<?>> result = securityContexts.load(exchange).map(context -> {
            Authentication authentication = context.getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof AdminIdentity admin)) {
                ResponseEntity<?> unauthorized = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(exchange,
                        HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "The session is not valid"));
                return unauthorized;
            }
            ResponseEntity<?> valid = ResponseEntity.ok(new SessionValidationResponse(admin.adminId(), admin.username(), "ADMIN"));
            return valid;
        }).defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body((Object) error(exchange, HttpStatus.UNAUTHORIZED,
                "SESSION_INVALID", "The session is not valid")));
        return result;
    }

    private GatewaySecurityErrorWriter.GatewaySecurityError error(ServerWebExchange exchange, HttpStatus status,
            String code, String message) {
        return new GatewaySecurityErrorWriter.GatewaySecurityError(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, exchange.getRequest().getURI().getPath(), Map.of(),
                exchange.getRequest().getHeaders().getFirst("X-Correlation-ID"));
    }
}
