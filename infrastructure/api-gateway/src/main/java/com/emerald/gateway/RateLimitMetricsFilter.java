package com.emerald.gateway;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Counts rejected requests with bounded route-name tags and no user/session identifiers. */
@Component
public class RateLimitMetricsFilter implements GlobalFilter, Ordered {
    private final MeterRegistry meters;

    public RateLimitMetricsFilter(MeterRegistry meters) {
        this.meters = meters;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).doFinally(signal -> {
            if (exchange.getResponse().getStatusCode() != null
                    && exchange.getResponse().getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                String routeName = route == null ? "local-handler" : route.getId();
                meters.counter("emerald.gateway.rate_limited.requests", "route", routeName).increment();
            }
        });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
