package com.emerald.gateway;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import tools.jackson.databind.json.JsonMapper;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationAndErrorFilter implements WebFilter {
    public static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final Logger log = LoggerFactory.getLogger(CorrelationAndErrorFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final JsonMapper jsonMapper;

    public CorrelationAndErrorFilter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
        String correlationId = supplied != null && SAFE_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
        long started = System.nanoTime();
        ServerWebExchange correlated = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> headers.set(CORRELATION_HEADER, correlationId))
                        .build())
                .response(exchange.getResponse())
                .build();
        exchange.getResponse().getHeaders().set(CORRELATION_HEADER, correlationId);
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(CORRELATION_HEADER, correlationId);
            return Mono.empty();
        });

        return chain.filter(correlated)
                .onErrorResume(error -> writeGatewayError(correlated, error, correlationId))
                .doFinally(signal -> {
                    int status = exchange.getResponse().getStatusCode() == null
                            ? HttpStatus.OK.value()
                            : exchange.getResponse().getStatusCode().value();
                    long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
                    log.info("gateway request completed correlationId={} method={} path={} status={} durationMs={}",
                            correlationId, exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(),
                            status, elapsedMillis);
                });
    }

    private Mono<Void> writeGatewayError(ServerWebExchange exchange, Throwable error, String correlationId) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }
        HttpStatus status;
        String code;
        String message;
        if (isNotFound(error)) {
            status = HttpStatus.NOT_FOUND;
            code = "ROUTE_NOT_FOUND";
            message = "No API route matches this request";
        } else if (isTimeout(error)) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            code = "GATEWAY_TIMEOUT";
            message = "A downstream service did not respond in time";
        } else {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = "GATEWAY_UNAVAILABLE";
            message = "A downstream service is unavailable";
        }
        GatewayError body = new GatewayError(Instant.now(), status.value(), status.getReasonPhrase(), code,
                message, exchange.getRequest().getURI().getPath(), correlationId);
        byte[] json;
        try {
            json = jsonMapper.writeValueAsBytes(body);
        } catch (Exception serializationError) {
            return Mono.error(serializationError);
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(CORRELATION_HEADER, correlationId);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(json);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof java.net.SocketTimeoutException) return true;
            if (cause.getClass().getSimpleName().contains("Timeout")) return true;
            if (cause instanceof WebClientRequestException requestError
                    && requestError.getCause() instanceof java.net.SocketTimeoutException) return true;
            if (cause instanceof ConnectException || cause instanceof UnknownHostException) return false;
        }
        return false;
    }

    private static boolean isNotFound(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResponseStatusException response
                    && response.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) return true;
            if (cause.getClass().getSimpleName().contains("NotFound")) return true;
        }
        return false;
    }

    public record GatewayError(Instant timestamp, int status, String error, String code, String message,
                               String path, String correlationId) { }
}
