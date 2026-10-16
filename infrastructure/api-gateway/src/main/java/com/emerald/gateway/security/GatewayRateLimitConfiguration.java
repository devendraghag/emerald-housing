package com.emerald.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayRateLimitConfiguration {
    @Bean
    KeyResolver authenticatedAdminKeyResolver() {
        return exchange -> exchange.getPrincipal().flatMap(principal -> {
            if (principal instanceof Authentication authentication
                    && authentication.getPrincipal() instanceof AdminIdentity admin) {
                return Mono.just("admin:" + admin.adminId());
            }
            return Mono.empty();
        }).switchIfEmpty(Mono.just("unauthenticated"));
    }

    @Bean
    RedisRateLimiter adminLoginRateLimiter(
            @Value("${GATEWAY_LOGIN_RATE_REPLENISH:2}") int replenishRate,
            @Value("${GATEWAY_LOGIN_RATE_BURST:5}") int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity);
    }
}
