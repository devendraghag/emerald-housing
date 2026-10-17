package com.emerald.member.security;

import com.emerald.member.auth.InternalCredentialController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

public class BackendSecurityContextFilter extends OncePerRequestFilter {
    private final GatewaySessionValidationClient validator;
    private final SecurityErrorWriter errors;
    private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();

    public BackendSecurityContextFilter(GatewaySessionValidationClient validator, SecurityErrorWriter errors) {
        this.validator = validator;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health")
                || request.getRequestURI().equals("/actuator/prometheus")
                || request.getRequestURI().equals("/internal/auth/credentials");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            validator.validate(request).ifPresent(identity -> {
                SecurityContext context = contexts.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity,
                        null, List.of(new SimpleGrantedAuthority("ROLE_" + identity.role()))));
                contexts.setContext(context);
            });
            chain.doFilter(request, response);
        } catch (org.springframework.web.client.RestClientException | IllegalStateException exception) {
            contexts.clearContext();
            errors.write(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "SESSION_VALIDATION_UNAVAILABLE", "Authentication validation is temporarily unavailable");
        } finally {
            contexts.clearContext();
        }
    }
}
