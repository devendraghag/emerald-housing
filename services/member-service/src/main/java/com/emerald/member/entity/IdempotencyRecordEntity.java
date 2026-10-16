package com.emerald.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(name = "uq_idempotency_scope_key",
                columnNames = {"actor_id", "operation", "idempotency_key"}),
        indexes = @Index(name = "ix_idempotency_expires_at", columnList = "expires_at"))
public class IdempotencyRecordEntity {
    @Id
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(nullable = false, length = 80)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecordEntity() { }

    public IdempotencyRecordEntity(UUID id, UUID actorId, String operation, String idempotencyKey,
            String requestFingerprint, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.actorId = actorId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.status = "IN_PROGRESS";
        this.responseStatus = 201;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getRequestFingerprint() { return requestFingerprint; }
    public String getStatus() { return status; }
    public int getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public void complete(int responseStatus, String responseBody) {
        this.status = "COMPLETED";
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }
}
