package com.emerald.member.client;

import com.emerald.member.config.CorrelationIdFilter;
import com.emerald.member.dto.SocietyFlatResponse;
import com.emerald.member.exception.FlatNotFoundException;
import com.emerald.member.exception.SocietyDependencyException;
import java.net.http.HttpTimeoutException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SocietyClient {
    private final RestClient client;
    private final JsonMapper jsonMapper;

    public SocietyClient(RestClient societyRestClient, JsonMapper jsonMapper) {
        this.client = societyRestClient;
        this.jsonMapper = jsonMapper;
    }

    public SocietyFlatResponse getFlat(UUID flatId) {
        try {
            String responseBody = client.get().uri("/api/v1/flats/{flatId}", flatId)
                    .header(CorrelationIdFilter.HEADER, MDC.get("correlationId") == null ? UUID.randomUUID().toString() : MDC.get("correlationId"))
                    .retrieve().body(String.class);
            SocietyFlatResponse result;
            try {
                result = responseBody == null ? null : jsonMapper.readValue(responseBody, SocietyFlatResponse.class);
            } catch (JacksonException ex) {
                throw new SocietyDependencyException(502, "SOCIETY_INVALID_RESPONSE",
                        "Society Service returned an invalid response", ex);
            }
            if (result == null || result.flatId() == null || result.status() == null)
                throw new SocietyDependencyException(502, "SOCIETY_INVALID_RESPONSE", "Society Service returned an invalid response", null);
            if (!result.flatId().equals(flatId))
                throw new SocietyDependencyException(502, "SOCIETY_INVALID_RESPONSE", "Society Service returned a different flat identifier", null);
            return result;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) throw new FlatNotFoundException(flatId);
            if (ex.getStatusCode().value() == 400) throw new SocietyDependencyException(400, "SOCIETY_INVALID_REQUEST", "Society Service rejected the flat lookup", ex);
            if (ex.getStatusCode().value() == 409) throw new SocietyDependencyException(409, "SOCIETY_CONFLICT", "Society Service reported a flat conflict", ex);
            throw new SocietyDependencyException(502, "SOCIETY_SERVICE_ERROR", "Society Service could not complete the flat lookup", ex);
        } catch (ResourceAccessException ex) {
            if (hasTimeout(ex)) throw new SocietyDependencyException(504, "SOCIETY_SERVICE_TIMEOUT", "Society Service timed out", ex);
            throw new SocietyDependencyException(503, "SOCIETY_SERVICE_UNAVAILABLE", "Society Service is unavailable", ex);
        } catch (RestClientException | NullPointerException ex) {
            throw new SocietyDependencyException(502, "SOCIETY_INVALID_RESPONSE", "Society Service returned an invalid response", ex);
        }
    }
    private static boolean hasTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) return true;
        return false;
    }
}
