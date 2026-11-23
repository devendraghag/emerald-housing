package com.emerald.member.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class KafkaConsumerBackpressureConfigTest {
    @Test
    void configuresABoundedKafkaPollBatchWithAnEnvironmentOverride() throws Exception {
        var source = new YamlPropertySourceLoader()
                .load("member-service", new ClassPathResource("application.yml"))
                .getFirst();

        assertEquals("${KAFKA_CONSUMER_MAX_POLL_RECORDS:25}", source.getProperty("spring.kafka.consumer.max-poll-records"));
    }
}
