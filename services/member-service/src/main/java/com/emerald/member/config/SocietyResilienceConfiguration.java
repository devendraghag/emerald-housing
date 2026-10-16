package com.emerald.member.config;

import com.emerald.member.exception.SocietyDependencyException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SocietyResilienceConfiguration {
    public static final String INSTANCE = "societyFlatLookup";

    @Bean
    CircuitBreakerRegistry societyCircuitBreakerRegistry(
            @Value("${resilience.society.sliding-window-size:10}") int windowSize,
            @Value("${resilience.society.minimum-calls:5}") int minimumCalls,
            @Value("${resilience.society.failure-rate-threshold:50}") float failureRate,
            @Value("${resilience.society.slow-call-rate-threshold:60}") float slowCallRate,
            @Value("${resilience.society.slow-call-duration:2s}") Duration slowCallDuration,
            @Value("${resilience.society.open-state-wait:15s}") Duration openStateWait,
            @Value("${resilience.society.half-open-permitted-calls:2}") int halfOpenCalls) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRate)
                .slowCallRateThreshold(slowCallRate)
                .slowCallDurationThreshold(slowCallDuration)
                .waitDurationInOpenState(openStateWait)
                .permittedNumberOfCallsInHalfOpenState(halfOpenCalls)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(error -> error instanceof SocietyDependencyException dependency
                        && dependency.getStatus() >= 500)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean(name = "societyFlatLookupCircuitBreaker")
    CircuitBreaker societyFlatLookupCircuitBreaker(CircuitBreakerRegistry societyCircuitBreakerRegistry) {
        CircuitBreaker breaker = societyCircuitBreakerRegistry.circuitBreaker(INSTANCE);
        breaker.getEventPublisher().onStateTransition(event ->
                org.slf4j.LoggerFactory.getLogger(SocietyResilienceConfiguration.class)
                        .info("Resilience circuit {} transitioned {} -> {}", INSTANCE,
                                event.getStateTransition().getFromState(), event.getStateTransition().getToState()));
        return breaker;
    }

    @Bean
    RetryRegistry societyRetryRegistry(
            @Value("${resilience.society.retry-max-attempts:3}") int maxAttempts,
            @Value("${resilience.society.retry-initial-interval:200ms}") Duration initialInterval,
            @Value("${resilience.society.retry-multiplier:2.0}") double multiplier,
            @Value("${resilience.society.retry-jitter:0.2}") double jitter,
            @Value("${resilience.society.retry-max-interval:1s}") Duration maxInterval) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        initialInterval, multiplier, jitter, maxInterval))
                .retryOnException(error -> error instanceof SocietyDependencyException dependency
                        && dependency.isRetryable())
                .build();
        return RetryRegistry.of(config);
    }

    @Bean(name = "societyFlatLookupRetry")
    Retry societyFlatLookupRetry(RetryRegistry societyRetryRegistry) {
        return societyRetryRegistry.retry(INSTANCE);
    }

    @Bean
    BulkheadRegistry societyBulkheadRegistry(
            @Value("${resilience.society.bulkhead-max-concurrent-calls:12}") int maxConcurrentCalls,
            @Value("${resilience.society.bulkhead-max-wait:0ms}") Duration maxWait) {
        return BulkheadRegistry.of(BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrentCalls)
                .maxWaitDuration(maxWait)
                .build());
    }

    @Bean(name = "societyFlatLookupBulkhead")
    Bulkhead societyFlatLookupBulkhead(BulkheadRegistry societyBulkheadRegistry) {
        return societyBulkheadRegistry.bulkhead(INSTANCE);
    }

    @Bean
    MeterBinder resilience4jMeters(CircuitBreakerRegistry societyCircuitBreakerRegistry,
            RetryRegistry societyRetryRegistry, BulkheadRegistry societyBulkheadRegistry) {
        return registry -> {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(societyCircuitBreakerRegistry).bindTo(registry);
            TaggedRetryMetrics.ofRetryRegistry(societyRetryRegistry).bindTo(registry);
            TaggedBulkheadMetrics.ofBulkheadRegistry(societyBulkheadRegistry).bindTo(registry);
        };
    }
}
