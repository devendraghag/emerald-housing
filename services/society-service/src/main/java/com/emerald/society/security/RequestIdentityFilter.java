package com.emerald.society.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdentityFilter extends OncePerRequestFilter {
    public static final String MEMBER_TOKEN_HEADER = "X-Member-Service-Token";
    private final GatewaySessionValidationClient validator;
    private final SecurityErrorWriter errors;
    private final byte[] memberToken;
    private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();

    public RequestIdentityFilter(GatewaySessionValidationClient validator, SecurityErrorWriter errors,
            @Value("${emerald.security.member-flat-read-token:}") String memberToken) {
        this.validator = validator;
        this.errors = errors;
        this.memberToken = memberToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health")
                || request.getRequestURI().equals("/actuator/prometheus");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String suppliedMemberToken = request.getHeader(MEMBER_TOKEN_HEADER);
        if (suppliedMemberToken != null) {
            if (!isFlatLookup(request) || memberToken.length < 32 || !MessageDigest.isEqual(memberToken,
                    suppliedMemberToken.getBytes(StandardCharsets.UTF_8))) {
                errors.write(request, response, HttpStatus.FORBIDDEN, "INTERNAL_CALL_FORBIDDEN",
                        "This internal operation is not permitted");
                return;
            }
            setIdentity("member-service", "SERVICE_MEMBER_FLAT_READ");
            try { chain.doFilter(request, response); }
            finally { contexts.clearContext(); }
            return;
        }
        try {
            validator.validate(request).ifPresent(identity -> setIdentity(identity, "ROLE_" + identity.role()));
            chain.doFilter(request, response);
        } catch (org.springframework.web.client.RestClientException | IllegalStateException exception) {
            contexts.clearContext();
            errors.write(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "SESSION_VALIDATION_UNAVAILABLE", "Authentication validation is temporarily unavailable");
        } finally {
            contexts.clearContext();
        }
    }

    private static boolean isFlatLookup(HttpServletRequest request) {
        return "GET".equals(request.getMethod())
                && request.getRequestURI().matches("/api/v1/flats/[0-9a-fA-F-]{36}");
    }

    private void setIdentity(SessionIdentityResponse identity, String authority) {
        setIdentity(identity, authority, identity);
    }

    private void setIdentity(String principal, String authority) {
        setIdentity(principal, authority, principal);
    }

    private void setIdentity(Object principal, String authority, Object credentials) {
        SecurityContext context = contexts.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority(authority))));
        contexts.setContext(context);
    }
}
