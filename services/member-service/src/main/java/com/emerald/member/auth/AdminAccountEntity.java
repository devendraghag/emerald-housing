package com.emerald.member.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_users")
public class AdminAccountEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.Version @Column(nullable=false) private long version;
    @Column(name="society_code",nullable=false,length=40) private String societyCode="EMERALD";
    @Column(name="member_id") private UUID memberId;
    public AdminAccountEntity(UUID id,String username,String passwordHash,String role,String societyCode,UUID memberId) {
        this.id=id;this.username=username;this.passwordHash=passwordHash;this.role=role;this.societyCode=societyCode;this.memberId=memberId;
        this.enabled=true;this.createdAt=Instant.now();this.updatedAt=this.createdAt;
    }
    public void permissions(String role,String societyCode,UUID memberId,boolean enabled) { this.role=role;this.societyCode=societyCode;this.memberId=memberId;this.enabled=enabled;this.updatedAt=Instant.now(); }
    public long getVersion() { return version; }
    public String getSocietyCode() { return societyCode; }
    public UUID getMemberId() { return memberId; }
    protected AdminAccountEntity() { }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
}
