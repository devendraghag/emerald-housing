package com.emerald.member.lostfound;

import java.time.Instant;
import java.util.UUID;

/** Event envelope published when a Lost and Found workflow changes. */
public record LostFoundEvent(
        UUID eventId,
        UUID reportId,
        String eventType,
        UUID actorId,
        Instant occurredAt,
        int schemaVersion,
        Instant timestamp,
        String correlationId) {

    public LostFoundEvent(UUID eventId, UUID reportId, String eventType, UUID actorId, Instant occurredAt) {
        this(eventId, reportId, eventType, actorId, occurredAt, 1, Instant.now(),
                java.util.Optional.ofNullable(org.slf4j.MDC.get("correlationId"))
                        .orElseGet(() -> UUID.randomUUID().toString()));
    }

    public static LostFoundEvent create(UUID reportId, String eventType, UUID actorId) {
        return new LostFoundEvent(UUID.randomUUID(), reportId, eventType, actorId, Instant.now());
    }
}
