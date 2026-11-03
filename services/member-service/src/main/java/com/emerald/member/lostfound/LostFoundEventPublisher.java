package com.emerald.member.lostfound;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LostFoundEventPublisher {
    public static final String TOPIC = "lost-found.events";

    private final KafkaTemplate<String, String> kafka;
    private final JdbcTemplate db;
    private final JsonMapper json;

    public LostFoundEventPublisher(KafkaTemplate<String, String> kafka, JdbcTemplate db, JsonMapper json) {
        this.kafka = kafka; this.db=db; this.json=json;
    }

    /** Called in the business transaction; the outbox row commits with the report update. */
    @Transactional
    public void publish(LostFoundEvent event) {
        db.update("INSERT INTO lost_found_event_outbox(event_id,report_id,event_type,actor_id,occurred_at) VALUES (?,?,?,?,?)",
                event.eventId(),event.reportId(),event.eventType(),event.actorId(),Timestamp.from(event.occurredAt()));
    }

    @Scheduled(fixedDelayString="${lost-found.outbox-poll:1000}")
    public void dispatch() {
        var batch=db.query("SELECT event_id,report_id,event_type,actor_id,occurred_at FROM lost_found_event_outbox ORDER BY created_at LIMIT 50",
                (rs,n)->new LostFoundEvent(rs.getObject("event_id",java.util.UUID.class),rs.getObject("report_id",java.util.UUID.class),rs.getString("event_type"),rs.getObject("actor_id",java.util.UUID.class),rs.getTimestamp("occurred_at").toInstant()));
        for(var event:batch) {
            try {
                kafka.send(TOPIC,event.reportId().toString(),json.writeValueAsString(event)).get();
                db.update("DELETE FROM lost_found_event_outbox WHERE event_id=?",event.eventId());
            } catch(Exception ex) {
                db.update("UPDATE lost_found_event_outbox SET attempts=attempts+1 WHERE event_id=?",event.eventId());
                break;
            }
        }
    }
}
