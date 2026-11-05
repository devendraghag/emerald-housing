package com.emerald.ai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class CookieCsrfValidationFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final SecurityErrorWriter errors;

    public CookieCsrfValidationFilter(SecurityErrorWriter errors) { this.errors = errors; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            chain.doFilter(request, response);
            return;
        }
        boolean xsrfCookie = request.getCookies() != null && java.util.Arrays.stream(request.getCookies())
                .anyMatch(cookie -> "XSRF-TOKEN".equals(cookie.getName()) && !cookie.getValue().isBlank());
        String xsrfHeader = request.getHeader("X-XSRF-TOKEN");
        if (!xsrfCookie || xsrfHeader == null || xsrfHeader.isBlank()) {
            errors.write(request, response, HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID", "A valid CSRF token is required");
            return;
        }
        chain.doFilter(request, response);
    }
}
