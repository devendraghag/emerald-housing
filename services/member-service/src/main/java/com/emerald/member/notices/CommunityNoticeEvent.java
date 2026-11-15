package com.emerald.member.notices;

import java.time.Instant;
import java.util.UUID;

public record CommunityNoticeEvent(UUID eventId, UUID noticeId, String eventType,
        UUID actorId, Instant occurredAt, int schemaVersion, Instant timestamp, String correlationId) {
    public CommunityNoticeEvent(UUID eventId, UUID noticeId, String eventType, UUID actorId, Instant occurredAt) {
        this(eventId, noticeId, eventType, actorId, occurredAt, 1, Instant.now(),
                java.util.Optional.ofNullable(org.slf4j.MDC.get("correlationId"))
                        .orElseGet(() -> UUID.randomUUID().toString()));
    }
    public static CommunityNoticeEvent create(UUID noticeId, String eventType, UUID actorId) {
        return new CommunityNoticeEvent(UUID.randomUUID(), noticeId, eventType, actorId, Instant.now());
    }
}
