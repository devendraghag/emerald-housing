package com.emerald.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.DelegatingServerLogoutHandler;
import org.springframework.web.server.WebSessionIdResolver;
import org.springframework.web.server.session.CookieWebSessionIdResolver;

@Configuration
public class GatewaySecurityConfiguration {
    @Bean
    ServerSecurityContextRepository securityContextRepository() {
        return new WebSessionServerSecurityContextRepository();
    }

    @Bean
    ServerCsrfTokenRepository csrfTokenRepository(@Value("${emerald.security.session-cookie-secure:false}") boolean secure) {
        CookieServerCsrfTokenRepository repository = CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").secure(secure).sameSite("Lax"));
        return repository;
    }

    @Bean
    WebSessionIdResolver webSessionIdResolver(
            @Value("${emerald.security.session-cookie-name:SESSION}") String cookieName,
            @Value("${emerald.security.session-cookie-secure:false}") boolean secure) {
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
        resolver.setCookieName(cookieName);
        resolver.addCookieInitializer(cookie -> cookie.path("/").httpOnly(true).secure(secure).sameSite("Lax"));
        return resolver;
    }

    @Bean
    SecurityWebFilterChain gatewaySecurityFilterChain(ServerHttpSecurity http,
            ServerSecurityContextRepository securityContexts, ServerCsrfTokenRepository csrfTokens,
            GatewaySecurityErrorWriter errors) {
        var logoutHandlers = new DelegatingServerLogoutHandler(
                new SecurityContextServerLogoutHandler(),
                new WebSessionServerLogoutHandler(),
                (exchange, authentication) -> csrfTokens.saveToken(exchange.getExchange(), null));

        http.securityContextRepository(securityContexts)
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
                        .accessDeniedHandler((exchange, denied) -> errors.write(exchange,
                                HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID", "A valid CSRF token is required")))
                .authorizeExchange(authorize -> authorize
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/api/v1/auth/csrf",
                                "/api/v1/auth/login", "/internal/auth/session").permitAll()
                        .pathMatchers("/actuator/metrics", "/actuator/metrics/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/**").hasRole("ADMIN")
                        .anyExchange().denyAll())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((exchange, error) -> errors.write(exchange, HttpStatus.UNAUTHORIZED,
                                "UNAUTHENTICATED", "Authentication is required"))
                        .accessDeniedHandler((exchange, error) -> errors.write(exchange, HttpStatus.FORBIDDEN,
                                "FORBIDDEN", "You do not have permission to perform this operation")))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .logoutHandler(logoutHandlers)
                        .logoutSuccessHandler((exchange, authentication) -> errors.write(exchange.getExchange(),
                                HttpStatus.OK, "LOGOUT_SUCCESS", "The session has been ended")))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }
}
