package com.emerald.member.entity;

import com.emerald.member.service.MemberStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "members")
public class MemberEntity {
    @Id private UUID id;
    @jakarta.persistence.Version @Column(nullable=false) private long version;
    public long getVersion() { return version; }
    @Column(name = "full_name", nullable = false, length = 160) private String fullName;
    @Column(name = "mobile_number", nullable = false, length = 10) private String mobileNumber;
    @Column(length = 254) private String email;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private MemberStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected MemberEntity() { }
    public MemberEntity(UUID id, String fullName, String mobileNumber, String email, MemberStatus status, Instant createdAt, Instant updatedAt) {
        this.id=id; this.fullName=fullName; this.mobileNumber=mobileNumber; this.email=email; this.status=status; this.createdAt=createdAt; this.updatedAt=updatedAt;
    }
    public UUID getId() { return id; } public String getFullName() { return fullName; }
    public String getMobileNumber() { return mobileNumber; } public String getEmail() { return email; }
    public MemberStatus getStatus() { return status; } public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void update(String name, String mobile, String email, Instant now) { this.fullName=name; this.mobileNumber=mobile; this.email=email; this.updatedAt=now; }
    public boolean deactivate(Instant now) { if (status == MemberStatus.INACTIVE) return false; status=MemberStatus.INACTIVE; updatedAt=now; return true; }
}
