package com.emerald.member.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityErrorWriter {
    private final JsonMapper objectMapper;

    public SecurityErrorWriter(JsonMapper objectMapper) { this.objectMapper = objectMapper; }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiSecurityError body = new ApiSecurityError(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, request.getRequestURI(), Map.of(), request.getHeader("X-Correlation-ID"));
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    public record ApiSecurityError(Instant timestamp, int status, String error, String code, String message,
                                   String path, Map<String, String> details, String correlationId) { }
}
