package com.emerald.member.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically measures committed consumer-group offsets against Kafka's current
 * log-end offsets for the event listener groups owned by Member Service.
 */
@Component
public class KafkaConsumerLagMetrics {
    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerLagMetrics.class);
    private static final Duration ADMIN_TIMEOUT = Duration.ofSeconds(10);
    private static final List<GroupTopic> TARGETS = List.of(
            new GroupTopic("community-notice-notifications", "community-notices.events"),
            new GroupTopic("lost-found-notifications", "lost-found.events"));

    private final AdminClient admin;
    private final MeterRegistry meters;
    private final Map<GroupTopic, AtomicLong> lagValues = new HashMap<>();

    public KafkaConsumerLagMetrics(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            MeterRegistry meters) {
        this.admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
        this.meters = meters;
        for (GroupTopic target : TARGETS) {
            AtomicLong lag = new AtomicLong(-1);
            lagValues.put(target, lag);
            meters.gauge("kafka.consumer.lag",
                    Tags.of("group", target.group(), "topic", target.topic()),
                    lag, AtomicLong::get);
        }
    }

    @Scheduled(fixedDelayString = "${kafka.consumer-lag.poll:15000}")
    public void refreshLag() {
        for (GroupTopic target : TARGETS) {
            try {
                lagValues.get(target).set(readLag(target));
            } catch (Exception error) {
                meters.counter("kafka.consumer.lag.poll.failures").increment();
                log.warn("Unable to refresh Kafka consumer lag group={} topic={}",
                        target.group(), target.topic(), error);
            }
        }
    }

    private long readLag(GroupTopic target) throws Exception {
        var descriptions = admin.describeTopics(List.of(target.topic()))
                .allTopicNames().get(ADMIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        var partitions = descriptions.get(target.topic()).partitions().stream()
                .map(partition -> new TopicPartition(target.topic(), partition.partition()))
                .toList();
        if (partitions.isEmpty()) {
            return 0;
        }

        Map<TopicPartition, OffsetSpec> latestRequests = new HashMap<>();
        Map<TopicPartition, OffsetSpec> beginningRequests = new HashMap<>();
        for (TopicPartition partition : partitions) {
            latestRequests.put(partition, OffsetSpec.latest());
            beginningRequests.put(partition, OffsetSpec.earliest());
        }
        Map<TopicPartition, Long> endOffsets = readOffsets(latestRequests);
        Map<TopicPartition, Long> beginningOffsets = readOffsets(beginningRequests);
        var committedOffsets = admin.listConsumerGroupOffsets(target.group())
                .partitionsToOffsetAndMetadata()
                .get(ADMIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        Map<TopicPartition, Long> committed = new HashMap<>();
        committedOffsets.forEach((partition, offset) ->
                committed.put(partition, offset.offset()));

        return calculateLag(partitions, committed, beginningOffsets, endOffsets);
    }

    private Map<TopicPartition, Long> readOffsets(Map<TopicPartition, OffsetSpec> requests)
            throws Exception {
        Map<TopicPartition, Long> offsets = new HashMap<>();
        admin.listOffsets(requests).all().get(ADMIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                .forEach((partition, result) -> offsets.put(partition, result.offset()));
        return offsets;
    }

    static long calculateLag(
            List<TopicPartition> partitions,
            Map<TopicPartition, Long> committedOffsets,
            Map<TopicPartition, Long> beginningOffsets,
            Map<TopicPartition, Long> endOffsets) {
        long total = 0;
        for (TopicPartition partition : partitions) {
            Long end = endOffsets.get(partition);
            Long committed = committedOffsets.get(partition);
            Long beginning = beginningOffsets.get(partition);
            if (end == null) {
                continue;
            }
            long current = committed != null ? committed : beginning != null ? beginning : end;
            if (beginning != null) {
                current = Math.max(current, beginning);
            }
            total += Math.max(0, end - current);
        }
        return total;
    }

    @PreDestroy
    public void close() {
        admin.close(Duration.ofSeconds(5));
    }

    private record GroupTopic(String group, String topic) {
    }
}
