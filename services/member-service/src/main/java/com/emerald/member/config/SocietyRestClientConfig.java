package com.emerald.member.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SocietyRestClientConfig {
    // A dedicated executor must not replace Boot's general application executor.
    @Bean(defaultCandidate = false, destroyMethod = "close")
    ExecutorService societyHttpExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("society-http-", 0).factory());
    }

    // The dependency ensures Spring closes the HTTP client before its executor.
    @Bean(destroyMethod = "close")
    HttpClient societyHttpClient(@Qualifier("societyHttpExecutor") ExecutorService executor,
            @Value("${society.service.connect-timeout:2s}") Duration connectTimeout) {
        return HttpClient.newBuilder().executor(executor).connectTimeout(connectTimeout).build();
    }

    @Bean
    RestClient societyRestClient(RestClient.Builder builder,
            @Qualifier("societyHttpClient") HttpClient httpClient,
            @Value("${society.service.base-url}") String baseUrl,
            @Value("${society.service.read-timeout:3s}") Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }
}
