package com.emerald.member.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.junit.jupiter.api.Test;

class KafkaProducerMetricsTest {
    @Test
    void averagesMatchingFiniteKafkaClientMetricsAndIgnoresOtherNames() {
        MetricName firstName = new MetricName("batch-size-avg", "producer-metrics", "", Map.of());
        MetricName secondName = new MetricName("batch-size-avg", "producer-node-metrics", "", Map.of());
        MetricName otherName = new MetricName("record-size-avg", "producer-metrics", "", Map.of());
        Metric first = metric(1_000.0);
        Metric second = metric(3_000.0);
        Metric other = metric(200.0);

        double result = KafkaProducerMetrics.average(
                Map.of(firstName, first, secondName, second, otherName, other), "batch-size-avg");

        assertEquals(2_000.0, result);
    }

    private Metric metric(double value) {
        Metric metric = mock(Metric.class);
        when(metric.metricValue()).thenReturn(value);
        return metric;
    }
}
