package com.emerald.member.lostfound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class LostFoundEventListener {
    private static final Logger log = LoggerFactory.getLogger(LostFoundEventListener.class);
    private final JdbcTemplate db;
    private final JsonMapper json;
    private final MeterRegistry meters;
    public LostFoundEventListener(JdbcTemplate db, JsonMapper json, MeterRegistry meters) { this.db=db; this.json=json; this.meters=meters; }

    @KafkaListener(topics = LostFoundEventPublisher.TOPIC, groupId = "lost-found-notifications")
    @Transactional
    public void onEvent(String payload) {
        LostFoundEvent event=json.readValue(payload,LostFoundEvent.class);
        if (db.update("INSERT INTO kafka_processed_events(flow,event_id) VALUES ('lost-found',?) ON CONFLICT DO NOTHING",event.eventId()) == 0) {
            meters.counter("kafka.messages.duplicate","flow","lost-found").increment();
            return;
        }
        db.update("""
                INSERT INTO in_app_notifications(event_id,recipient_id,report_id,event_type)
                SELECT DISTINCT ?, au.id, ?, ?
                FROM admin_users au
                LEFT JOIN account_roles ar ON ar.account_id=au.id AND ar.role_code IN ('ADMIN','SOCIETY_ADMIN')
                WHERE au.enabled AND au.id<>? AND (
                    ar.account_id IS NOT NULL OR au.member_id IN (
                        SELECT fm.member_id
                        FROM flat_memberships fm
                        JOIN members m ON m.id=fm.member_id AND m.status='ACTIVE'
                        JOIN lost_found_reports r ON r.id=? AND r.flat_id=fm.flat_id
                        WHERE fm.end_date IS NULL AND fm.start_date<=CAST(CURRENT_TIMESTAMP AT TIME ZONE 'UTC' AS DATE)
                        UNION
                        SELECT f.linked_member_id
                        FROM family_members f
                        JOIN flat_memberships fm ON fm.id=f.owner_membership_id
                        JOIN members m ON m.id=f.linked_member_id AND m.status='ACTIVE'
                        JOIN lost_found_reports r ON r.id=? AND r.flat_id=f.flat_id
                        WHERE f.active AND f.linked_member_id IS NOT NULL AND fm.end_date IS NULL
                          AND fm.start_date<=CAST(CURRENT_TIMESTAMP AT TIME ZONE 'UTC' AS DATE)
                    )
                )
                ON CONFLICT DO NOTHING
                """, event.eventId(),event.reportId(),event.eventType(),event.actorId(),event.reportId(),event.reportId());
        meters.counter("kafka.messages.consumed","flow","lost-found").increment();
        log.info("Consumed lost-and-found event eventId={} schemaVersion={} correlationId={}",event.eventId(),event.schemaVersion(),event.correlationId());
    }
}
