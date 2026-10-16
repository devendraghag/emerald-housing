package com.emerald.gateway.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import java.time.Duration;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.core.publisher.Mono;

@Component
public class AdminAuthenticationManager implements ReactiveAuthenticationManager {
    private final WebClient memberClient;
    private final String credentialUrl;
    private final String serviceToken;

    public AdminAuthenticationManager(WebClient.Builder builder,
            @Value("${emerald.security.member-credential-url}") String credentialUrl,
            @Value("${emerald.security.gateway-authentication-token:}") String serviceToken,
            @Value("${emerald.security.credential-connect-timeout:2s}") Duration connectTimeout,
            @Value("${emerald.security.credential-response-timeout:3s}") Duration responseTimeout) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeout.toMillis()))
                .responseTimeout(responseTimeout);
        this.memberClient = builder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
        this.credentialUrl = credentialUrl;
        this.serviceToken = serviceToken;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken)) {
            return Mono.error(new BadCredentialsException("Username or password is invalid"));
        }
        if (serviceToken.getBytes(StandardCharsets.UTF_8).length < 32) {
            return Mono.error(new AuthServiceUnavailableException());
        }
        CredentialRequest request = new CredentialRequest(authentication.getName(),
                String.valueOf(authentication.getCredentials()));
        return memberClient.post().uri(credentialUrl)
                .header("X-Gateway-Authentication-Token", serviceToken)
                .bodyValue(request)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 401) return Mono.error(new BadCredentialsException("Username or password is invalid"));
                    if (response.statusCode().isError()) return Mono.error(new AuthServiceUnavailableException());
                    return response.bodyToMono(AdminIdentityResponse.class);
                })
                .switchIfEmpty(Mono.error(new AuthServiceUnavailableException()))
                .map(identity -> {
                    if (identity.adminId() == null || identity.username() == null || !"ADMIN".equals(identity.role())) {
                        throw new AuthServiceUnavailableException();
                    }
                    AdminIdentity principal = new AdminIdentity(identity.adminId(), identity.username());
                    return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                })
                .onErrorMap(error -> error instanceof BadCredentialsException || error instanceof AuthServiceUnavailableException
                        ? error : error instanceof WebClientRequestException
                        ? new AuthServiceUnavailableException() : new AuthServiceUnavailableException());
    }
}
