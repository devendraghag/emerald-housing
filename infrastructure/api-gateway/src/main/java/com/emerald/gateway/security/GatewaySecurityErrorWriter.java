package com.emerald.gateway.security;

import java.time.Instant;
import java.util.Map;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GatewaySecurityErrorWriter {
    private final JsonMapper jsonMapper;
    public GatewaySecurityErrorWriter(JsonMapper jsonMapper) { this.jsonMapper = jsonMapper; }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        GatewaySecurityError body = new GatewaySecurityError(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, exchange.getRequest().getURI().getPath(), Map.of(),
                exchange.getRequest().getHeaders().getFirst("X-Correlation-ID"));
        byte[] bytes = jsonMapper.writeValueAsBytes(body);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    public record GatewaySecurityError(Instant timestamp, int status, String error, String code,
                                       String message, String path, Map<String, String> details,
                                       String correlationId) { }
}
