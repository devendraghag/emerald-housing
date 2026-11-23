package com.emerald.member.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Samples Kafka's native producer metrics through the shared KafkaTemplate producer. */
@Component
public class KafkaProducerMetrics {
    private static final Logger log = LoggerFactory.getLogger(KafkaProducerMetrics.class);
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry meters;
    private final ProducerStats stats = new ProducerStats();

    public KafkaProducerMetrics(KafkaTemplate<String, String> kafka, MeterRegistry meters) {
        this.kafka = kafka;
        this.meters = meters;
        Tags tags = Tags.of("client", "member-service");
        meters.gauge("kafka.producer.batch.size.avg", tags, stats, value -> value.batchSizeAvg.get());
        meters.gauge("kafka.producer.records.per.request.avg", tags, stats,
                value -> value.recordsPerRequestAvg.get());
        meters.gauge("kafka.producer.compression.rate", tags, stats,
                value -> value.compressionRate.get());
        meters.gauge("kafka.producer.record.size.avg", tags, stats,
                value -> value.recordSizeAvg.get());
    }

    @Scheduled(fixedDelayString = "@ANNOTATION@")
    public void refresh() {
        try {
            Map<MetricName, ? extends Metric> producerMetrics = kafka.execute(Producer::metrics);
            stats.batchSizeAvg.set(average(producerMetrics, "batch-size-avg"));
            stats.recordsPerRequestAvg.set(average(producerMetrics, "records-per-request-avg"));
            stats.compressionRate.set(average(producerMetrics, "compression-rate-avg"));
            stats.recordSizeAvg.set(average(producerMetrics, "record-size-avg"));
        } catch (Exception error) {
            meters.counter("kafka.producer.metrics.poll.failures").increment();
            log.warn("Unable to refresh Kafka producer metrics", error);
        }
    }

    static double average(Map<MetricName, ? extends Metric> metrics, String name) {
        return metrics.entrySet().stream()
                .filter(entry -> name.equals(entry.getKey().name()))
                .map(entry -> entry.getValue().metricValue())
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToDouble(Number::doubleValue)
                .filter(Double::isFinite)
                .average()
                .orElse(-1.0);
    }

    private static final class ProducerStats {
        private final AtomicReference<Double> batchSizeAvg = new AtomicReference<>(-1.0);
        private final AtomicReference<Double> recordsPerRequestAvg = new AtomicReference<>(-1.0);
        private final AtomicReference<Double> compressionRate = new AtomicReference<>(-1.0);
        private final AtomicReference<Double> recordSizeAvg = new AtomicReference<>(-1.0);
    }
}
