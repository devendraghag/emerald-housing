package com.emerald.member.lostfound;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import tools.jackson.databind.json.JsonMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.support.SendResult;

@Component
public class LostFoundEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LostFoundEventPublisher.class);
    public static final String TOPIC = "lost-found.events";

    private final KafkaTemplate<String, String> kafka;
    private final JdbcTemplate db;
    private final JsonMapper json;
    private final MeterRegistry meters;

    public LostFoundEventPublisher(KafkaTemplate<String, String> kafka, JdbcTemplate db, JsonMapper json, MeterRegistry meters) {
        this.kafka = kafka; this.db=db; this.json=json; this.meters=meters;
        meters.gauge("kafka.outbox.waiting", java.util.List.of(Tag.of("flow","lost-found")), db,
                jdbc -> jdbc.queryForObject("SELECT count(*) FROM lost_found_event_outbox", Long.class));
    }

    /** Called in the business transaction; the outbox row commits with the report update. */
    @Transactional
    public void publish(LostFoundEvent event) {
        db.update("INSERT INTO lost_found_event_outbox(event_id,report_id,event_type,actor_id,occurred_at,schema_version,event_timestamp,correlation_id) VALUES (?,?,?,?,?,?,?,?)",
                event.eventId(),event.reportId(),event.eventType(),event.actorId(),Timestamp.from(event.occurredAt()),event.schemaVersion(),Timestamp.from(event.timestamp()),event.correlationId());
    }

    @Scheduled(fixedDelayString="${lost-found.outbox-poll:1000}")
    public void dispatch() {
        UUID claim=UUID.randomUUID();
        var batch=db.query("WITH picked AS (SELECT event_id FROM lost_found_event_outbox WHERE claimed_until IS NULL OR claimed_until < now() ORDER BY created_at LIMIT 50 FOR UPDATE SKIP LOCKED) UPDATE lost_found_event_outbox o SET claim_token=?,claimed_until=now()+interval '180 seconds' FROM picked p WHERE o.event_id=p.event_id RETURNING o.event_id,o.report_id,o.event_type,o.actor_id,o.occurred_at,o.schema_version,o.event_timestamp,o.correlation_id",
                (rs,n)->new LostFoundEvent(rs.getObject("event_id",UUID.class),rs.getObject("report_id",UUID.class),rs.getString("event_type"),rs.getObject("actor_id",UUID.class),rs.getTimestamp("occurred_at").toInstant(),rs.getInt("schema_version"),rs.getTimestamp("event_timestamp").toInstant(),rs.getString("correlation_id")),claim);
        var pending = new ArrayList<Pending>();
        for(var event:batch) {
            try {
                CompletableFuture<SendResult<String,String>> send =
                        kafka.send(TOPIC,event.reportId().toString(),json.writeValueAsString(event));
                pending.add(new Pending(event, send));
            } catch(Exception ex) {
                fail(event, claim, ex);
            }
        }
        for(var item:pending) {
            var event = item.event();
            try {
                item.send().get();
                db.update("DELETE FROM lost_found_event_outbox WHERE event_id=? AND claim_token=?",event.eventId(),claim);
                meters.counter("kafka.messages.sent","flow","lost-found").increment();
                log.info("Published lost-and-found event eventId={} correlationId={}",event.eventId(),event.correlationId());
            } catch(Exception ex) {
                fail(event, claim, ex);
            }
        }
    }

    private void fail(LostFoundEvent event, UUID claim, Exception error) {
        db.update("UPDATE lost_found_event_outbox SET attempts=attempts+1,claim_token=NULL,claimed_until=NULL WHERE event_id=? AND claim_token=?",event.eventId(),claim);
        meters.counter("kafka.messages.failed","flow","lost-found","stage","outbox").increment();
        log.error("Failed publishing lost-and-found event eventId={}",event.eventId(),error);
    }

    private record Pending(LostFoundEvent event, CompletableFuture<SendResult<String,String>> send) {}
}
