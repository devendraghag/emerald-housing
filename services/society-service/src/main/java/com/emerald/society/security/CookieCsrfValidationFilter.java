package com.emerald.society.security;

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
        String cookieToken = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) for (Cookie cookie : cookies) if ("XSRF-TOKEN".equals(cookie.getName())) cookieToken = cookie.getValue();
        String headerToken = request.getHeader("X-XSRF-TOKEN");
        // Gateway performs the authoritative CSRF token comparison. Requiring the cookie and
        // non-simple header here also rejects browser form posts sent directly to a backend.
        if (cookieToken == null || cookieToken.isBlank() || headerToken == null || headerToken.isBlank()) {
            errors.write(request, response, HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID", "A valid CSRF token is required");
            return;
        }
        chain.doFilter(request, response);
    }
}
