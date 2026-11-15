package com.emerald.member.notices;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CommunityNoticeEventListener {
    private static final Logger log = LoggerFactory.getLogger(CommunityNoticeEventListener.class);
    private final JdbcTemplate db;
    private final JsonMapper json;
    private final MeterRegistry meters;
    public CommunityNoticeEventListener(JdbcTemplate db, JsonMapper json, MeterRegistry meters) { this.db=db; this.json=json; this.meters=meters; }

    @KafkaListener(topics=CommunityNoticeEventPublisher.TOPIC, groupId="community-notice-notifications")
    @Transactional
    public void onEvent(String payload) {
        CommunityNoticeEvent event=json.readValue(payload,CommunityNoticeEvent.class);
        if (db.update("INSERT INTO kafka_processed_events(flow,event_id) VALUES ('community-notices',?) ON CONFLICT DO NOTHING",event.eventId()) == 0) {
            meters.counter("kafka.messages.duplicate","flow","community-notices").increment();
            return;
        }
        db.update("""
                INSERT INTO community_notice_notifications(event_id,recipient_id,notice_id,event_type)
                SELECT DISTINCT ?, au.id, ?, ?
                FROM admin_users au
                LEFT JOIN account_roles ar ON ar.account_id=au.id AND ar.role_code IN ('ADMIN','SOCIETY_ADMIN')
                WHERE au.enabled AND au.id<>? AND (
                    ar.account_id IS NOT NULL OR au.member_id IN (
                        SELECT fm.member_id
                        FROM flat_memberships fm
                        JOIN members m ON m.id=fm.member_id AND m.status='ACTIVE'
                        JOIN community_notices n ON n.id=? AND n.audience_scope<>'ADMIN_ONLY'
                        WHERE fm.end_date IS NULL
                          AND fm.start_date<=CAST(CURRENT_TIMESTAMP AT TIME ZONE 'UTC' AS DATE)
                          AND (n.audience_scope='SOCIETY' OR EXISTS (
                              SELECT 1 FROM community_notice_flats nf
                              WHERE nf.notice_id=n.id AND nf.flat_id=fm.flat_id))
                        UNION
                        SELECT f.linked_member_id
                        FROM family_members f
                        JOIN flat_memberships fm ON fm.id=f.owner_membership_id
                        JOIN members m ON m.id=f.linked_member_id AND m.status='ACTIVE'
                        JOIN community_notices n ON n.id=? AND n.audience_scope<>'ADMIN_ONLY'
                        WHERE f.active AND f.linked_member_id IS NOT NULL AND fm.end_date IS NULL
                          AND fm.start_date<=CAST(CURRENT_TIMESTAMP AT TIME ZONE 'UTC' AS DATE)
                          AND (n.audience_scope='SOCIETY' OR EXISTS (
                              SELECT 1 FROM community_notice_flats nf
                              WHERE nf.notice_id=n.id AND nf.flat_id=fm.flat_id))
                    )
                ) ON CONFLICT DO NOTHING
                """,event.eventId(),event.noticeId(),event.eventType(),event.actorId(),event.noticeId(),event.noticeId());
        meters.counter("kafka.messages.consumed","flow","community-notices").increment();
        log.info("Consumed community notice event eventId={} schemaVersion={} correlationId={}",event.eventId(),event.schemaVersion(),event.correlationId());
    }
}
