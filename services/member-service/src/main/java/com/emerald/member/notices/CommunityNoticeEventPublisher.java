package com.emerald.member.notices;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CommunityNoticeEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(CommunityNoticeEventPublisher.class);
    public static final String TOPIC = "community-notices.events";
    private final KafkaTemplate<String,String> kafka;
    private final JdbcTemplate db;
    private final JsonMapper json;
    private final MeterRegistry meters;

    public CommunityNoticeEventPublisher(KafkaTemplate<String,String> kafka, JdbcTemplate db, JsonMapper json, MeterRegistry meters) {
        this.kafka=kafka; this.db=db; this.json=json; this.meters=meters;
        meters.gauge("kafka.outbox.waiting", java.util.List.of(io.micrometer.core.instrument.Tag.of("flow","community-notices")), db,
                jdbc -> jdbc.queryForObject("SELECT count(*) FROM community_notice_outbox", Long.class));
    }

    @Transactional
    public void publish(CommunityNoticeEvent event) {
        db.update("INSERT INTO community_notice_outbox(event_id,notice_id,event_type,actor_id,occurred_at,schema_version,event_timestamp,correlation_id) VALUES (?,?,?,?,?,?,?,?)",
                event.eventId(), event.noticeId(), event.eventType(), event.actorId(), Timestamp.from(event.occurredAt()), event.schemaVersion(), Timestamp.from(event.timestamp()), event.correlationId());
    }

    @Scheduled(fixedDelayString="${community-notices.outbox-poll:1000}")
    public void dispatch() {
        UUID claim=UUID.randomUUID();
        var batch=db.query("WITH picked AS (SELECT event_id FROM community_notice_outbox WHERE claimed_until IS NULL OR claimed_until < now() ORDER BY created_at LIMIT 50 FOR UPDATE SKIP LOCKED) UPDATE community_notice_outbox o SET claim_token=?,claimed_until=now()+interval '180 seconds' FROM picked p WHERE o.event_id=p.event_id RETURNING o.event_id,o.notice_id,o.event_type,o.actor_id,o.occurred_at,o.schema_version,o.event_timestamp,o.correlation_id",
                (rs,n)->new CommunityNoticeEvent(rs.getObject("event_id",java.util.UUID.class),
                        rs.getObject("notice_id",java.util.UUID.class),rs.getString("event_type"),
                        rs.getObject("actor_id",java.util.UUID.class),rs.getTimestamp("occurred_at").toInstant(),rs.getInt("schema_version"),rs.getTimestamp("event_timestamp").toInstant(),rs.getString("correlation_id")), claim);
        for(var event:batch) {
            try {
                kafka.send(TOPIC,event.noticeId().toString(),json.writeValueAsString(event)).get();
                db.update("DELETE FROM community_notice_outbox WHERE event_id=? AND claim_token=?",event.eventId(),claim);
                meters.counter("kafka.messages.sent","flow","community-notices").increment();
                log.info("Published community notice event eventId={} correlationId={}",event.eventId(),event.correlationId());
            } catch(Exception ex) {
                db.update("UPDATE community_notice_outbox SET attempts=attempts+1,claim_token=NULL,claimed_until=NULL WHERE event_id=? AND claim_token=?",event.eventId(),claim);
                meters.counter("kafka.messages.failed","flow","community-notices","stage","outbox").increment();
                log.error("Failed publishing community notice event eventId={}",event.eventId(),ex);
            }
        }
    }
}
