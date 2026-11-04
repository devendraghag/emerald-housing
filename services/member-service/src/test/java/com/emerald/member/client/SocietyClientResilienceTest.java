package com.emerald.member.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.emerald.member.dto.SocietyFlatResponse;
import com.emerald.member.exception.SocietyDependencyException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class SocietyClientResilienceTest {
    private WireMockServer dependency;
    private CircuitBreaker breaker;
    private Bulkhead bulkhead;
    private SocietyClient client;

    @BeforeEach
    void start() {
        dependency = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        dependency.start();
        breaker = CircuitBreaker.of("society-test", CircuitBreakerConfig.custom()
                .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(150))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordException(error -> error instanceof SocietyDependencyException failure && failure.getStatus() >= 500)
                .build());
        bulkhead = Bulkhead.of("society-test", BulkheadConfig.custom()
                .maxConcurrentCalls(4).maxWaitDuration(Duration.ZERO).build());
        client = newClient(breaker, bulkhead);
    }

    @AfterEach
    void stop() {
        dependency.stop();
    }

    @Test
    void circuitOpensAfterPhysicalFailuresAndClosesAfterSuccessfulHalfOpenProbe() throws Exception {
        UUID flatId = UUID.randomUUID();
        dependency.stubFor(get(urlPathMatching("/api/v1/flats/.*")).willReturn(serviceUnavailable()));
        assertThrows(SocietyDependencyException.class, () -> client.getFlat(flatId));
        assertThrows(SocietyDependencyException.class, () -> client.getFlat(flatId));
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        SocietyDependencyException open = assertThrows(SocietyDependencyException.class, () -> client.getFlat(flatId));
        assertEquals("SOCIETY_CIRCUIT_OPEN", open.getCode());
        dependency.verify(2, getRequestedFor(urlPathMatching("/api/v1/flats/.*")));

        Thread.sleep(200);
        UUID floorId = UUID.randomUUID();
        dependency.resetAll();
        dependency.stubFor(get(urlEqualTo("/api/v1/flats/" + flatId)).willReturn(okJson(
                "{\"flatId\":\"" + flatId + "\",\"flatNumber\":\"A-1\",\"floorId\":\""
                        + floorId + "\",\"floorNumber\":1,\"status\":\"ACTIVE\"}")));
        SocietyFlatResponse response = client.getFlat(flatId);
        assertEquals(flatId, response.flatId());
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }

    @Test
    void fullBulkheadRejectsAdditionalDependencyWorkWithoutQueueing() throws Exception {
        bulkhead = Bulkhead.of("society-capacity-test", BulkheadConfig.custom()
                .maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build());
        client = newClient(CircuitBreaker.ofDefaults("society-capacity-test"), bulkhead);
        UUID flatId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        dependency.stubFor(get(urlPathMatching("/api/v1/flats/.*")).willReturn(aResponse()
                .withFixedDelay(900)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"flatId\":\"" + flatId + "\",\"flatNumber\":\"A-1\",\"floorId\":\""
                        + floorId + "\",\"floorNumber\":1,\"status\":\"ACTIVE\"}")));
        var worker = Executors.newSingleThreadExecutor();
        try {
            var first = worker.submit(() -> client.getFlat(flatId));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (bulkhead.getMetrics().getAvailableConcurrentCalls() != 0 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(0, bulkhead.getMetrics().getAvailableConcurrentCalls(), "first call should hold the only permit");
            SocietyDependencyException rejected = assertThrows(SocietyDependencyException.class,
                    () -> client.getFlat(UUID.randomUUID()));
            assertEquals("SOCIETY_CAPACITY_EXCEEDED", rejected.getCode());
            assertEquals(flatId, first.get(5, TimeUnit.SECONDS).flatId());
            dependency.verify(1, getRequestedFor(urlPathMatching("/api/v1/flats/.*")));
        } finally {
            worker.shutdownNow();
        }
    }

    private SocietyClient newClient(CircuitBreaker circuitBreaker, Bulkhead callBulkhead) {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        RestClient restClient = RestClient.builder().baseUrl(dependency.baseUrl()).requestFactory(requestFactory).build();
        Retry retry = Retry.of("society-test", RetryConfig.custom().maxAttempts(1).build());
        return new SocietyClient(restClient, JsonMapper.builder().build(), "", circuitBreaker, retry,
                callBulkhead, new SimpleMeterRegistry());
    }
}
