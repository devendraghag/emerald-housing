package com.emerald.society.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "floors")
public class FloorEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "society_id", nullable = false)
    private SocietyEntity society;
    @Column(name = "floor_number", nullable = false) private int floorNumber;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false) private Instant updatedAt;
    protected FloorEntity() { }
    public FloorEntity(UUID id, SocietyEntity society, int floorNumber) { this.id=id;this.society=society;this.floorNumber=floorNumber; }
    public UUID getId() { return id; }
    public UUID getSocietyId() { return society.getId(); }
    public int getFloorNumber() { return floorNumber; }
    public void changeFloorNumber(int floorNumber) { this.floorNumber=floorNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
