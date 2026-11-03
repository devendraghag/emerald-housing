package com.emerald.member.notices;

import java.time.Instant;
import java.util.UUID;

public record CommunityNoticeEvent(UUID eventId, UUID noticeId, String eventType,
        UUID actorId, Instant occurredAt) {
    public static CommunityNoticeEvent create(UUID noticeId, String eventType, UUID actorId) {
        return new CommunityNoticeEvent(UUID.randomUUID(), noticeId, eventType, actorId, Instant.now());
    }
}
