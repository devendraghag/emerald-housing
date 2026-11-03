package com.emerald.member.lostfound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LostFoundEventListener {
    private final JdbcTemplate db;
    private final JsonMapper json;
    public LostFoundEventListener(JdbcTemplate db, JsonMapper json) { this.db=db; this.json=json; }

    @KafkaListener(topics = LostFoundEventPublisher.TOPIC, groupId = "lost-found-notifications")
    public void onEvent(String payload) {
        LostFoundEvent event=json.readValue(payload,LostFoundEvent.class);
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
    }
}
