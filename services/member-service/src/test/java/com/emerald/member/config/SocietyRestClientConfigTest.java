package com.emerald.member.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;

class SocietyRestClientConfigTest {
    private record TaskObservation(int index, boolean virtual) { }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
                .withUserConfiguration(SocietyRestClientConfig.class)
                .withPropertyValues("society.service.base-url=http://localhost:8081");
    }

    @Test
    void createsSocietyClientWithBootProvidedBuilder() {
        contextRunner()
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RestClient.Builder.class);
                    assertThat(context).hasBean("societyRestClient");
                    assertThat(context.getBean("societyHttpClient", HttpClient.class).executor()).contains(
                            context.getBean("societyHttpExecutor", ExecutorService.class));
                });
    }

    @Test
    @Timeout(15)
    void numericTaskBatchUsesVirtualThreadsAndResourcesCloseWithContext() {
        var executorRef = new AtomicReference<ExecutorService>();
        var clientRef = new AtomicReference<HttpClient>();
        contextRunner().run(context -> {
            var executor = context.getBean("societyHttpExecutor", ExecutorService.class);
            executorRef.set(executor);
            clientRef.set(context.getBean("societyHttpClient", HttpClient.class));
            var futures = IntStream.rangeClosed(1, 8)
                    .mapToObj(index -> executor.submit(() -> new TaskObservation(index, Thread.currentThread().isVirtual())))
                    .toList();
            var observations = futures.stream()
                    .map(future -> assertDoesNotThrow(() -> future.get(2, TimeUnit.SECONDS)))
                    .toList();
            assertThat(observations).allMatch(TaskObservation::virtual);
            assertThat(observations).extracting(TaskObservation::index)
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, 8).boxed().toList());
        });
        assertThat(clientRef.get().isTerminated()).isTrue();
        assertThat(executorRef.get().isTerminated()).isTrue();
    }

    @Test
    @Timeout(15)
    void potentiallyInfiniteTaskSourceIsBoundedBeforeSubmission() {
        contextRunner().run(context -> {
            var executor = context.getBean("societyHttpExecutor", ExecutorService.class);
            var futures = Stream.iterate(1, index -> index + 1).limit(3)
                    .map(index -> executor.submit(() -> index))
                    .toList();
            var results = futures.stream()
                    .map(future -> assertDoesNotThrow(() -> future.get(2, TimeUnit.SECONDS)))
                    .toList();
            assertThat(results).containsExactly(1, 2, 3);
        });
    }

    @Test
    @Timeout(20)
    void configuredTransportSendsRequestsAndRetainsReadTimeout() {
        var server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        try {
            server.stubFor(get(urlEqualTo("/ready")).willReturn(ok("ready")));
            server.stubFor(get(urlEqualTo("/slow")).willReturn(ok("late").withFixedDelay(1500)));
            contextRunner().withPropertyValues("society.service.base-url=" + server.baseUrl(),
                    "society.service.read-timeout=500ms").run(context -> {
                var client = context.getBean("societyRestClient", RestClient.class);
                assertThat(client.get().uri("/ready").retrieve().body(String.class)).isEqualTo("ready");
                assertThatThrownBy(() -> client.get().uri("/slow").retrieve().body(String.class))
                        .isInstanceOf(ResourceAccessException.class);
            });
        } finally {
            server.stop();
        }
    }
}
