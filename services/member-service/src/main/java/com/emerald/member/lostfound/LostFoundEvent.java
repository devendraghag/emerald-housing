package com.emerald.member.lostfound;

import java.time.Instant;
import java.util.UUID;

/** Event envelope published when a Lost and Found workflow changes. */
public record LostFoundEvent(
        UUID eventId,
        UUID reportId,
        String eventType,
        UUID actorId,
        Instant occurredAt) {

    public static LostFoundEvent create(UUID reportId, String eventType, UUID actorId) {
        return new LostFoundEvent(UUID.randomUUID(), reportId, eventType, actorId, Instant.now());
    }
}
