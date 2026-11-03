package com.emerald.member.notices;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CommunityNoticeEventListener {
    private final JdbcTemplate db;
    private final JsonMapper json;
    public CommunityNoticeEventListener(JdbcTemplate db, JsonMapper json) { this.db=db; this.json=json; }

    @KafkaListener(topics=CommunityNoticeEventPublisher.TOPIC, groupId="community-notice-notifications")
    public void onEvent(String payload) {
        CommunityNoticeEvent event=json.readValue(payload,CommunityNoticeEvent.class);
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
    }
}
