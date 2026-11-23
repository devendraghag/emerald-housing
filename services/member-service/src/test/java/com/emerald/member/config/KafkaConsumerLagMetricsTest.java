package com.emerald.member.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class KafkaConsumerLagMetricsTest {
    @Test
    void sumsLagAcrossPartitionsAndUsesBeginningOffsetBeforeFirstCommit() {
        TopicPartition first = new TopicPartition("events", 0);
        TopicPartition second = new TopicPartition("events", 1);

        long lag = KafkaConsumerLagMetrics.calculateLag(
                List.of(first, second),
                Map.of(first, 15L),
                Map.of(first, 0L, second, 10L),
                Map.of(first, 20L, second, 30L));

        assertEquals(25L, lag);
    }

    @Test
    void doesNotReportNegativeLagIfCommittedOffsetIsAheadOfObservedEndOffset() {
        TopicPartition partition = new TopicPartition("events", 0);

        long lag = KafkaConsumerLagMetrics.calculateLag(
                List.of(partition),
                Map.of(partition, 42L),
                Map.of(partition, 0L),
                Map.of(partition, 40L));

        assertEquals(0L, lag);
    }

    @Test
    void countsOnlyRetainedRecordsWhenCommittedOffsetPrecedesLogStart() {
        TopicPartition partition = new TopicPartition("events", 0);

        long lag = KafkaConsumerLagMetrics.calculateLag(
                List.of(partition),
                Map.of(partition, 3L),
                Map.of(partition, 8L),
                Map.of(partition, 12L));

        assertEquals(4L, lag);
    }
}
