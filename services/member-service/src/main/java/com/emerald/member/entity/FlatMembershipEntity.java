package com.emerald.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "flat_memberships")
public class FlatMembershipEntity implements MembershipPeriod {
    @Id private UUID id;
    @jakarta.persistence.Version @Column(nullable=false) private long version;
    public long getVersion() { return version; }
    @Column(name = "member_id", nullable = false) private UUID memberId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", insertable = false, updatable = false)
    private MemberEntity member;
    @Column(name = "flat_id", nullable = false) private UUID flatId;
    @Column(name = "relationship_type", nullable = false, length = 20) private String relationshipType;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected FlatMembershipEntity() { }
    public FlatMembershipEntity(UUID id, UUID memberId, UUID flatId, String relationshipType, LocalDate startDate, Instant now) {
        this.id=id; this.memberId=memberId; this.flatId=flatId; this.relationshipType=relationshipType; this.startDate=startDate; this.createdAt=now; this.updatedAt=now;
    }
    public UUID getId(){return id;} public UUID getMemberId(){return memberId;} public UUID getFlatId(){return flatId;}
    public String getRelationshipType(){return relationshipType;} public LocalDate getStartDate(){return startDate;} public LocalDate getEndDate(){return endDate;}
    public Instant getUpdatedAt(){return updatedAt;}
    public void end(LocalDate endDate, Instant now) { this.endDate = endDate; this.updatedAt = now; }
}
