package com.emerald.society.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "societies")
public class SocietyEntity {
    @Id private UUID id;
    @Column(name = "society_code", nullable = false, unique = true, length = 32) private String societyCode;
    @Column(nullable = false, length = 200) private String name;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false) private Instant updatedAt;
    protected SocietyEntity() { }
    public UUID getId() { return id; }
    public String getSocietyCode() { return societyCode; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
