package com.emerald.member.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.emerald.member.dto.SocietyFlatResponse;
import com.emerald.member.exception.FlatNotFoundException;
import com.emerald.member.exception.SocietyDependencyException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class SocietyClientWireMockTest {
    private WireMockServer wireMock;
    private SocietyClient client;

    @BeforeEach void start() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        configureClient(Duration.ofSeconds(10));
    }
    @AfterEach void stop() { wireMock.stop(); }

    private void configureClient(Duration readTimeout) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(readTimeout);
        client = new SocietyClient(RestClient.builder().baseUrl(wireMock.baseUrl()).requestFactory(factory).build(),
                JsonMapper.builder().build());
    }

    @Test void deserializesSuccessfulFlatAndForwardsCorrelationId() {
        UUID flatId = UUID.randomUUID(), floorId = UUID.randomUUID();
        wireMock.stubFor(get(urlEqualTo("/api/v1/flats/" + flatId)).willReturn(okJson("{\"flatId\":\"" + flatId + "\",\"flatNumber\":\"A-1\",\"floorId\":\"" + floorId + "\",\"floorNumber\":1,\"status\":\"ACTIVE\"}")));
        SocietyFlatResponse actual = client.getFlat(flatId);
        assertEquals(flatId, actual.flatId());
        wireMock.verify(getRequestedFor(urlEqualTo("/api/v1/flats/" + flatId)).withHeader("X-Correlation-ID", matching("[A-Za-z0-9._-]{1,64}")));
    }

    @Test void mapsMissingFlatToNotFound() {
        UUID id = UUID.randomUUID(); wireMock.stubFor(get(anyUrl()).willReturn(notFound()));
        assertThrows(FlatNotFoundException.class, () -> client.getFlat(id));
    }

    @Test void mapsDependencyServerErrorToBadGateway() {
        wireMock.stubFor(get(anyUrl()).willReturn(serverError()));
        SocietyDependencyException error = assertThrows(SocietyDependencyException.class, () -> client.getFlat(UUID.randomUUID()));
        assertEquals(502, error.getStatus());
    }

    @Test void mapsSocietyBadRequestAndConflictWithoutHidingTheirOrigin() {
        UUID badRequestId = UUID.randomUUID();
        wireMock.stubFor(get(urlEqualTo("/api/v1/flats/" + badRequestId)).willReturn(badRequest()));
        SocietyDependencyException bad = assertThrows(SocietyDependencyException.class, () -> client.getFlat(badRequestId));
        assertEquals(400, bad.getStatus());
        UUID conflictId = UUID.randomUUID();
        wireMock.stubFor(get(urlEqualTo("/api/v1/flats/" + conflictId)).willReturn(aResponse().withStatus(409)));
        SocietyDependencyException conflict = assertThrows(SocietyDependencyException.class, () -> client.getFlat(conflictId));
        assertEquals(409, conflict.getStatus());
    }

    @Test void mapsConnectionFailureToServiceUnavailable() {
        wireMock.stop();
        SocietyDependencyException error = assertThrows(SocietyDependencyException.class,
                () -> client.getFlat(UUID.randomUUID()));
        assertEquals(503, error.getStatus());
    }

    @Test void mapsIncompleteSuccessBodyToBadGateway() {
        wireMock.stubFor(get(anyUrl()).willReturn(okJson("{}")));
        SocietyDependencyException error = assertThrows(SocietyDependencyException.class,
                () -> client.getFlat(UUID.randomUUID()));
        assertEquals(502, error.getStatus());
    }

    @Test void mapsSlowResponseToGatewayTimeout() {
        configureClient(Duration.ofMillis(500));
        wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withFixedDelay(4000).withBody("{}")));
        SocietyDependencyException error = assertThrows(SocietyDependencyException.class, () -> client.getFlat(UUID.randomUUID()));
        assertEquals(504, error.getStatus());
    }

}
