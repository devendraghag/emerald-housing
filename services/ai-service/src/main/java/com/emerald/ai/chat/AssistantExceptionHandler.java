package com.emerald.ai.chat;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class AssistantExceptionHandler {
    @ExceptionHandler(AssistantProviderUnavailableException.class)
    public ResponseEntity<ApiError> providerUnavailable(AssistantProviderUnavailableException exception,
                                                        HttpServletRequest request) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
                status.getReasonPhrase(), "AI_PROVIDER_UNAVAILABLE",
                "The assistant is temporarily unavailable. Please try again shortly.",
                request.getRequestURI(), Map.of(), request.getHeader("X-Correlation-ID")));
    }

    public record ApiError(Instant timestamp, int status, String error, String code, String message,
                           String path, Map<String, String> details, String correlationId) {
    }
}
