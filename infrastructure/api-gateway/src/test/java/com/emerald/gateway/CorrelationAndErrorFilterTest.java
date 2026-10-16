package com.emerald.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import tools.jackson.databind.json.JsonMapper;
import reactor.core.publisher.Mono;

class CorrelationAndErrorFilterTest {
    private final CorrelationAndErrorFilter filter = new CorrelationAndErrorFilter(JsonMapper.builder().build());

    @Test
    void forwardsSafeCorrelationIdAndReturnsItInResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/society")
                        .header(CorrelationAndErrorFilter.CORRELATION_HEADER, "request-123")
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block(Duration.ofSeconds(1));

        assertThat(exchange.getRequest().getHeaders().getFirst(CorrelationAndErrorFilter.CORRELATION_HEADER))
                .isEqualTo("request-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationAndErrorFilter.CORRELATION_HEADER))
                .isEqualTo("request-123");
    }

    @Test
    void replacesUnsafeCorrelationId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/society")
                        .header(CorrelationAndErrorFilter.CORRELATION_HEADER, "unsafe id")
                        .build());
        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();

        filter.filter(exchange, request -> {
            forwardedExchange.set(request);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        String responseId = exchange.getResponse().getHeaders()
                .getFirst(CorrelationAndErrorFilter.CORRELATION_HEADER);
        assertThat(responseId).isNotBlank().matches("[0-9a-f-]{36}");
        assertThat(forwardedExchange.get().getRequest().getHeaders()
                .getFirst(CorrelationAndErrorFilter.CORRELATION_HEADER))
                .isEqualTo(responseId);
    }

    @Test
    void emitsOnlyOneCorrelationIdWhenDownstreamAlreadyReturnsIt() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/society")
                        .header(CorrelationAndErrorFilter.CORRELATION_HEADER, "request-123")
                        .build());

        filter.filter(exchange, request -> {
            request.getResponse().getHeaders().add(CorrelationAndErrorFilter.CORRELATION_HEADER, "request-123");
            return request.getResponse().setComplete();
        }).block(Duration.ofSeconds(1));

        assertThat(exchange.getResponse().getHeaders().get(CorrelationAndErrorFilter.CORRELATION_HEADER))
                .containsExactly("request-123");
    }

    @Test
    void mapsConnectionFailureToServiceUnavailable() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/members").build());

        filter.filter(exchange, ignored -> Mono.error(new ConnectException("connection refused")))
                .block(Duration.ofSeconds(1));

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void mapsDownstreamTimeoutToGatewayTimeout() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/members").build());

        filter.filter(exchange, ignored -> Mono.error(new TimeoutException("downstream timeout")))
                .block(Duration.ofSeconds(1));

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(504);
    }

    @Test
    void preservesNotFoundForUnmatchedRoute() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/no-route").build());

        filter.filter(exchange, ignored -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .block(Duration.ofSeconds(1));

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(404);
    }
}
