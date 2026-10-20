package com.emerald.member.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GatewaySessionValidationClient {
    public static final String VALIDATION_TOKEN_HEADER = "X-Internal-Session-Validation-Token";
    private final RestClient client;
    private final String cookieName;
    private final String validationToken;

    public GatewaySessionValidationClient(
            @Value("${emerald.security.gateway-session-validation-url:http://localhost:8080/internal/auth/session}") String url,
            @Value("${emerald.security.session-cookie-name:SESSION}") String cookieName,
            @Value("${emerald.security.session-validation-token:}") String validationToken,
            @Value("${emerald.security.validation-connect-timeout:2s}") Duration connectTimeout,
            @Value("${emerald.security.validation-read-timeout:3s}") Duration readTimeout) {
        this.cookieName = cookieName;
        this.validationToken = validationToken;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().requestFactory(factory).baseUrl(url).build();
    }

    public Optional<SessionIdentityResponse> validate(HttpServletRequest request) {
        String sessionId = sessionId(request);
        if (sessionId == null) return Optional.empty();
        if (validationToken.length() < 32) throw new IllegalStateException("Session validation is not configured");
        SessionIdentityResponse identity = client.get().uri("")
                .header(VALIDATION_TOKEN_HEADER, validationToken)
                .header(HttpHeaders.COOKIE, cookieName + "=" + sessionId)
                .header("X-Correlation-ID", Optional.ofNullable(request.getHeader("X-Correlation-ID")).orElse(""))
                .exchange((outgoing, incoming) -> {
                    if (incoming.getStatusCode().value() == 401) return null;
                    if (!incoming.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("Session validation service rejected the request");
                    }
                    return incoming.bodyTo(SessionIdentityResponse.class);
                });
        if (identity == null || identity.adminId() == null || identity.username() == null || identity.role() == null
                || !java.util.Set.of("ADMIN","SOCIETY_ADMIN","COMMITTEE","RESIDENT").contains(identity.role())) return Optional.empty();
        return Optional.of(identity);
    }

    private String sessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (cookieName.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
