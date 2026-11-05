package com.emerald.ai.tools;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SocietyMaintenanceClient {
    private final RestClient client;
    private final String sessionCookieName;

    public SocietyMaintenanceClient(
            @Value("${society.service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${emerald.security.session-cookie-name:SESSION}") String sessionCookieName,
            @Value("${society.service.connect-timeout:2s}") Duration connectTimeout,
            @Value("${society.service.read-timeout:3s}") Duration readTimeout) {
        this.sessionCookieName = sessionCookieName;
        var httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    public MaintenanceStatus status(UUID requestId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalStateException("Resident session is unavailable");
        return client.get().uri("/api/v1/rental/maintenance/{id}", requestId)
                .header(HttpHeaders.COOKIE, sessionCookieName + "=" + sessionId)
                .retrieve().body(MaintenanceStatus.class);
    }

    public record MaintenanceStatus(UUID requestId, String status) { }
}
