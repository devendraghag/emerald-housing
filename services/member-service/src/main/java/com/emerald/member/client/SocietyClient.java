package com.emerald.member.client;

import com.emerald.member.config.CorrelationIdFilter;
import com.emerald.member.dto.SocietyFlatResponse;
import com.emerald.member.exception.FlatNotFoundException;
import com.emerald.member.exception.SocietyDependencyException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SocietyClient {
    private final RestClient client;
    private final JsonMapper jsonMapper;
    private final String internalToken;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private final MeterRegistry meters;

    @Autowired
    public SocietyClient(RestClient societyRestClient, JsonMapper jsonMapper,
            @Value("${emerald.security.member-flat-read-token:}") String internalToken,
            CircuitBreaker societyFlatLookupCircuitBreaker,
            Retry societyFlatLookupRetry,
            Bulkhead societyFlatLookupBulkhead,
            MeterRegistry meters) {
        this.client = societyRestClient;
        this.jsonMapper = jsonMapper;
        this.internalToken = internalToken;
        this.circuitBreaker = societyFlatLookupCircuitBreaker;
        this.retry = societyFlatLookupRetry;
        this.bulkhead = societyFlatLookupBulkhead;
        this.meters = meters;
    }

    /** Keeps existing focused client construction possible without changing the test sources. */
    public SocietyClient(RestClient societyRestClient, JsonMapper jsonMapper) {
        this(societyRestClient, jsonMapper, "", CircuitBreaker.ofDefaults("societyFlatLookup-test"),
                Retry.ofDefaults("societyFlatLookup-test"), Bulkhead.ofDefaults("societyFlatLookup-test"),
                new SimpleMeterRegistry());
    }

    public SocietyFlatResponse getFlat(UUID flatId) {
        Supplier<SocietyFlatResponse> singleAttempt = CircuitBreaker.decorateSupplier(circuitBreaker,
                Bulkhead.decorateSupplier(bulkhead, () -> getFlatOnce(flatId)));
        try {
            // Retry is outermost: each physical GET obtains a fresh circuit-breaker and bulkhead permit.
            return Retry.decorateSupplier(retry, singleAttempt).get();
        } catch (CallNotPermittedException ex) {
            throw new SocietyDependencyException(503, "SOCIETY_CIRCUIT_OPEN",
                    "Society Service is temporarily unavailable", ex);
        } catch (BulkheadFullException ex) {
            throw new SocietyDependencyException(503, "SOCIETY_CAPACITY_EXCEEDED",
                    "Society Service validation capacity is temporarily full", ex);
        }
    }

    private SocietyFlatResponse getFlatOnce(UUID flatId) {
        Timer.Sample sample = Timer.start(meters);
        try {
            return performFlatLookup(flatId);
        } catch (SocietyDependencyException exception) {
            if (exception.getStatus() == 504) {
                meters.counter("emerald.society.flat.lookup.timeouts").increment();
            }
            throw exception;
        } finally {
            sample.stop(Timer.builder("emerald.society.flat.lookup")
                    .description("Duration of one physical Society flat lookup attempt")
                    .register(meters));
        }
    }

    private SocietyFlatResponse performFlatLookup(UUID flatId) {
        try {
            String correlationId = MDC.get("correlationId");
            String responseBody = client.get().uri("/api/v1/flats/{flatId}", flatId)
                    .header("X-Member-Service-Token", internalToken)
                    .header(CorrelationIdFilter.HEADER,
                            correlationId == null ? UUID.randomUUID().toString() : correlationId)
                    .retrieve().body(String.class);
            SocietyFlatResponse result;
            try {
                result = responseBody == null ? null : jsonMapper.readValue(responseBody, SocietyFlatResponse.class);
            } catch (JacksonException ex) {
                throw new SocietyDependencyException(502, "SOCIETY_INVALID_RESPONSE",
                        "Society Service returned an invalid response", ex);
            }
            if (result == null || result.flatId() == null || result.status() == null)
                throw new SocietyDependencyException(502, "SOCIETY_INVALID_RESPONSE",
                        "Society Service returned an invalid response", null);
            if (!result.flatId().equals(flatId))
                throw new SocietyDependencyException(502, "SOCIETY_INVALID_RESPONSE",
                        "Society Service returned a different flat identifier", null);
            return result;
        } catch (RestClientResponseException ex) {
            int remoteStatus = ex.getStatusCode().value();
            if (remoteStatus == 404) throw new FlatNotFoundException(flatId);
            if (remoteStatus == 400) throw new SocietyDependencyException(400, "SOCIETY_INVALID_REQUEST",
                    "Society Service rejected the flat lookup", ex);
            if (remoteStatus == 409) throw new SocietyDependencyException(409, "SOCIETY_CONFLICT",
                    "Society Service reported a flat conflict", ex);
            boolean transientStatus = remoteStatus == 502 || remoteStatus == 503 || remoteStatus == 504;
            throw new SocietyDependencyException(502, "SOCIETY_SERVICE_ERROR",
                    "Society Service could not complete the flat lookup", ex, transientStatus);
        } catch (ResourceAccessException ex) {
            if (hasTimeout(ex))
                throw new SocietyDependencyException(504, "SOCIETY_SERVICE_TIMEOUT",
                        "Society Service timed out", ex, true);
            throw new SocietyDependencyException(503, "SOCIETY_SERVICE_UNAVAILABLE",
                    "Society Service is unavailable", ex, true);
        } catch (RestClientException | NullPointerException ex) {
            throw new SocietyDependencyException(502, "SOCIETY_INVALID_RESPONSE",
                    "Society Service returned an invalid response", ex);
        }
    }

    private static boolean hasTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) return true;
        }
        return false;
    }
}
