package com.emerald.gateway.security;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import io.micrometer.core.instrument.Metrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final ReactiveAuthenticationManager authenticationManager;
    private final ServerSecurityContextRepository securityContexts;
    private final ServerCsrfTokenRepository csrfTokens;
    private final RedisRateLimiter adminLoginRateLimiter;

    @Autowired
    public AuthController(ReactiveAuthenticationManager authenticationManager,
            ServerSecurityContextRepository securityContexts, ServerCsrfTokenRepository csrfTokens,
            RedisRateLimiter adminLoginRateLimiter) {
        this.authenticationManager = authenticationManager;
        this.securityContexts = securityContexts;
        this.csrfTokens = csrfTokens;
        this.adminLoginRateLimiter = adminLoginRateLimiter;
    }

    public AuthController(ReactiveAuthenticationManager authenticationManager,
            ServerSecurityContextRepository securityContexts, ServerCsrfTokenRepository csrfTokens) {
        this(authenticationManager, securityContexts, csrfTokens, new RedisRateLimiter(2, 5));
    }

    @GetMapping("/csrf")
    public Mono<CsrfResponse> csrf(ServerWebExchange exchange) {
        @SuppressWarnings("unchecked")
        Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
        if (token == null) return Mono.error(new IllegalStateException("CSRF token was not initialized"));
        return token.map(value -> new CsrfResponse(value.getToken(), value.getHeaderName(), value.getParameterName()));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<?>> login(@Valid @RequestBody LoginRequest request, ServerWebExchange exchange) {
        return adminLoginRateLimiter.isAllowed("admin-login", "unauthenticated")
                .flatMap(rate -> rate.isAllowed()
                        ? authenticate(request, exchange)
                        : loginRateLimited(exchange))
                .onErrorResume(rateLimiterFailure -> {
                    Metrics.counter("emerald.gateway.rate_limiter.errors", "route", "login").increment();
                    return Mono.just((ResponseEntity<?>) ResponseEntity
                            .status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(error(exchange, HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMITER_UNAVAILABLE",
                                    "Login protection is temporarily unavailable")));
                });
    }

    private Mono<ResponseEntity<?>> loginRateLimited(ServerWebExchange exchange) {
        Metrics.counter("emerald.gateway.rate_limited.requests", "route", "login").increment();
        return Mono.just((ResponseEntity<?>) ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "1")
                .body(error(exchange, HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED",
                        "Too many login attempts; wait before trying again")));
    }

    private Mono<ResponseEntity<?>> authenticate(LoginRequest request, ServerWebExchange exchange) {
        Authentication credentials = UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password());
        return authenticationManager.authenticate(credentials)
                .<ResponseEntity<?>>flatMap(authentication -> {
                    SecurityContext context = new SecurityContextImpl(authentication);
                    return exchange.getSession()
                            .flatMap(session -> session.changeSessionId().thenReturn(session))
                            .flatMap(session -> securityContexts.save(exchange, context)
                                    .then(csrfTokens.saveToken(exchange, null))
                                    .then(Mono.fromSupplier(() -> (ResponseEntity<?>) ResponseEntity.ok(
                                            new LoginResponse(((AdminIdentity) authentication.getPrincipal()).adminId(),
                                                    ((AdminIdentity) authentication.getPrincipal()).username(), ((AdminIdentity) authentication.getPrincipal()).role(), true)))));
                })
                .onErrorResume(BadCredentialsException.class, exception -> Mono.<ResponseEntity<?>>just(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(exchange, HttpStatus.UNAUTHORIZED,
                                "INVALID_CREDENTIALS", "Username or password is invalid"))))
                .onErrorResume(AuthServiceUnavailableException.class, exception -> Mono.<ResponseEntity<?>>just(
                        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(exchange,
                                HttpStatus.SERVICE_UNAVAILABLE, "AUTHENTICATION_SERVICE_UNAVAILABLE",
                                "Credential validation is temporarily unavailable"))))
                .onErrorResume(exception -> Mono.<ResponseEntity<?>>just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(error(exchange, HttpStatus.SERVICE_UNAVAILABLE, "AUTHENTICATION_UNAVAILABLE",
                                "Authentication is temporarily unavailable"))));
    }

    @GetMapping("/me")
    public Mono<LoginResponse> currentAdmin() {
        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication)
                .flatMap(authentication -> {
                    AdminIdentity admin = (AdminIdentity) authentication.getPrincipal();
                    if (authenticationManager instanceof AdminAuthenticationManager accounts) {
                        return accounts.current(admin.adminId())
                                .map(identity -> new LoginResponse(identity.adminId(), identity.username(), identity.role(), true))
                                .onErrorMap(org.springframework.web.reactive.function.client.WebClientResponseException.Unauthorized.class,
                                        error -> new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is unavailable"))
                                .onErrorMap(error -> !(error instanceof org.springframework.web.server.ResponseStatusException),
                                        error -> new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account validation is unavailable"));
                    }
                    return Mono.just(new LoginResponse(admin.adminId(), admin.username(), admin.role(), true));
                });
    }

    private GatewaySecurityErrorWriter.GatewaySecurityError error(ServerWebExchange exchange, HttpStatus status,
            String code, String message) {
        return new GatewaySecurityErrorWriter.GatewaySecurityError(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, exchange.getRequest().getURI().getPath(), Map.of(),
                exchange.getRequest().getHeaders().getFirst("X-Correlation-ID"));
    }

    public record LoginRequest(@jakarta.validation.constraints.NotBlank String username,
                               @jakarta.validation.constraints.NotBlank String password) { }
}
