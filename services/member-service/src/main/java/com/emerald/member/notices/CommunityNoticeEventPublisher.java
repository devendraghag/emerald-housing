package com.emerald.member.notices;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CommunityNoticeEventPublisher {
    public static final String TOPIC = "community-notices.events";
    private final KafkaTemplate<String,String> kafka;
    private final JdbcTemplate db;
    private final JsonMapper json;

    public CommunityNoticeEventPublisher(KafkaTemplate<String,String> kafka, JdbcTemplate db, JsonMapper json) {
        this.kafka=kafka; this.db=db; this.json=json;
    }

    @Transactional
    public void publish(CommunityNoticeEvent event) {
        db.update("INSERT INTO community_notice_outbox(event_id,notice_id,event_type,actor_id,occurred_at) VALUES (?,?,?,?,?)",
                event.eventId(), event.noticeId(), event.eventType(), event.actorId(), Timestamp.from(event.occurredAt()));
    }

    @Scheduled(fixedDelayString="${community-notices.outbox-poll:1000}")
    public void dispatch() {
        var batch=db.query("SELECT event_id,notice_id,event_type,actor_id,occurred_at FROM community_notice_outbox ORDER BY created_at LIMIT 50",
                (rs,n)->new CommunityNoticeEvent(rs.getObject("event_id",java.util.UUID.class),
                        rs.getObject("notice_id",java.util.UUID.class),rs.getString("event_type"),
                        rs.getObject("actor_id",java.util.UUID.class),rs.getTimestamp("occurred_at").toInstant()));
        for(var event:batch) {
            try {
                kafka.send(TOPIC,event.noticeId().toString(),json.writeValueAsString(event)).get();
                db.update("DELETE FROM community_notice_outbox WHERE event_id=?",event.eventId());
            } catch(Exception ex) {
                db.update("UPDATE community_notice_outbox SET attempts=attempts+1 WHERE event_id=?",event.eventId());
                break;
            }
        }
    }
}
