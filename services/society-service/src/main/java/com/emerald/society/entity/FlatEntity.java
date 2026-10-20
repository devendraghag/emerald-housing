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

@Entity
@Table(name = "flats")
public class FlatEntity {
    @Id private UUID id;
    @jakarta.persistence.Version @Column(nullable=false) private long version;
    public long getVersion() { return version; }
    @Column(name = "society_id", nullable = false) private UUID societyId;
    @Column(name = "floor_id", nullable = false) private UUID floorId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "society_id", insertable = false, updatable = false)
    private SocietyEntity society;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_id", insertable = false, updatable = false)
    private FloorEntity floor;
    @Column(name = "flat_number", nullable = false, length = 40) private String flatNumber;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected FlatEntity() { }
    public FlatEntity(UUID id, UUID societyId, UUID floorId, String flatNumber, String status, Instant now) {
        this.id = id; this.societyId = societyId; this.floorId = floorId;
        this.flatNumber = flatNumber; this.status = status; this.createdAt = now; this.updatedAt = now;
    }
    public UUID getId() { return id; }
    public UUID getSocietyId() { return societyId; }
    public FloorEntity getFloor() { return floor; }
    public void setFloorReference(FloorEntity floor) {
        if (!floorId.equals(floor.getId())) throw new IllegalArgumentException("Floor reference must match floorId");
        this.floor = floor;
    }
    public UUID getFloorId() { return floorId; }
    public String getFlatNumber() { return flatNumber; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void update(UUID newFloorId, String newFlatNumber, Instant now) {
        this.floorId = newFloorId;
        this.flatNumber = newFlatNumber;
        this.updatedAt = now;
    }
    public boolean deactivate(Instant now) {
        if ("INACTIVE".equals(status)) return false;
        status = "INACTIVE";
        updatedAt = now;
        return true;
    }
}
