package com.emerald.member.notices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "TRACING_OTLP_ENABLED=false",
        "management.otlp.metrics.export.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "community-notices.outbox-poll=60000",
        "lost-found.outbox-poll=60000"
})
@Testcontainers(disabledWithoutDocker = true)
class CommunityNoticeKafkaIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.0")).withKraft();

    @DynamicPropertySource
    static void dependencies(DynamicPropertyRegistry properties) {
        properties.add("app.datasource.writer.jdbc-url", POSTGRES::getJdbcUrl);
        properties.add("app.datasource.writer.username", POSTGRES::getUsername);
        properties.add("app.datasource.writer.password", POSTGRES::getPassword);
        properties.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired CommunityNoticeEventPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @Test
    void outboxDispatchPublishesTheEventAndRemovesItOnlyAfterBrokerAck() {
        UUID noticeId = UUID.randomUUID();
        CommunityNoticeEvent event = CommunityNoticeEvent.create(noticeId, "CommunityNoticePublished", UUID.randomUUID());
        publisher.publish(event);
        assertEquals(1, jdbc.queryForObject("select count(*) from community_notice_outbox where event_id=?",
                Integer.class, event.eventId()));

        publisher.dispatch();

        assertEquals(0, jdbc.queryForObject("select count(*) from community_notice_outbox where event_id=?",
                Integer.class, event.eventId()));
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(CommunityNoticeEventPublisher.TOPIC));
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            CommunityNoticeEvent received = null;
            while (received == null && System.nanoTime() < deadline) {
                var records = consumer.poll(Duration.ofMillis(500));
                for (var record : records) {
                    CommunityNoticeEvent candidate = json.readValue(record.value(), CommunityNoticeEvent.class);
                    if (event.eventId().equals(candidate.eventId())) received = candidate;
                }
            }
            assertNotNull(received, "published event should be readable from Kafka");
            assertEquals(event, received);
        }
    }
}
