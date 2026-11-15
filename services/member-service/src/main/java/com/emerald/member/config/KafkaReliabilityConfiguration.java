package com.emerald.member.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.util.backoff.FixedBackOff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;

@Configuration
public class KafkaReliabilityConfiguration {
    private static final Logger log = LoggerFactory.getLogger(KafkaReliabilityConfiguration.class);
    @Bean
    NewTopic communityNoticeDeadLetterTopic() {
        return TopicBuilder.name("community-notices.events.DLT").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic lostFoundDeadLetterTopic() {
        return TopicBuilder.name("lost-found.events.DLT").partitions(1).replicas(1).build();
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka, MeterRegistry meters) {
        ConsumerRecordRecoverer deadLetter = (ConsumerRecord<?, ?> record, Exception error) -> {
            try {
                RecordHeaders headers = new RecordHeaders();
                record.headers().forEach(header -> headers.add(header));
                headers.add("x-original-topic", record.topic().getBytes(StandardCharsets.UTF_8));
                headers.add("x-dead-letter-error", error.toString().getBytes(StandardCharsets.UTF_8));
                kafka.send(new ProducerRecord<>(record.topic() + ".DLT", null, (String) record.key(),
                        (String) record.value(), headers)).get();
                String flow = record.topic().startsWith("community-notices") ? "community-notices" : "lost-found";
                meters.counter("kafka.messages.failed", "flow", flow, "stage", "consumer").increment();
                log.error("Routed Kafka record to DLT topic={} partition={} offset={}", record.topic(), record.partition(), record.offset(), error);
            } catch (Exception publishFailure) {
                throw new IllegalStateException("Could not publish failed Kafka record to its dead-letter topic", publishFailure);
            }
        };
        return new DefaultErrorHandler(deadLetter, new FixedBackOff(1_000L, 2L));
    }
}
