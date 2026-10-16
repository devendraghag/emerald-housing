package com.emerald.society.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds a low-sensitivity instance identifier so local replica routing can be observed. */
@Component
public class InstanceIdResponseHeaderFilter extends OncePerRequestFilter {
    private final String instanceId;
    private final boolean enabled;

    public InstanceIdResponseHeaderFilter(
            @Value("${app.instance.id:local}") String instanceId,
            @Value("${app.instance.header-enabled:true}") boolean enabled) {
        this.instanceId = instanceId;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (enabled && instanceId != null && !instanceId.isBlank()) {
            response.setHeader("X-Instance-ID", instanceId);
        }
        chain.doFilter(request, response);
    }
}
