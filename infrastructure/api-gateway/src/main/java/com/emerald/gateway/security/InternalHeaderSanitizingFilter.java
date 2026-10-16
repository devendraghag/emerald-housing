package com.emerald.gateway.security;

import java.util.Set;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class InternalHeaderSanitizingFilter implements GlobalFilter, Ordered {
    private static final Set<String> INTERNAL_HEADERS = Set.of(
            "X-Gateway-Authentication-Token", "X-Internal-Session-Validation-Token", "X-Member-Service-Token",
            "X-Authenticated-User", "X-Authenticated-Role", "X-User", "X-Role");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> INTERNAL_HEADERS.forEach(headers::remove)).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
