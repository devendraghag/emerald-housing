package com.emerald.member.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);
    public static final String HEADER = "X-Correlation-ID";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String id = supplied != null && SAFE.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
        MDC.put("correlationId", id);
        response.setHeader(HEADER, id);
        try { chain.doFilter(request, response); }
        finally { LOGGER.info("HTTP {} {} completed status={}", request.getMethod(), request.getRequestURI(), response.getStatus()); MDC.remove("correlationId"); }
    }
}
