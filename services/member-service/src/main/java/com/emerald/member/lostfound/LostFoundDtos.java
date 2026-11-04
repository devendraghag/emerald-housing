package com.emerald.member.lostfound;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class LostFoundDtos {
    private LostFoundDtos() { }
    public record CreateReport(@NotBlank @Size(max=20) @Pattern(regexp="(?i)LOST|FOUND", message="Report type must be LOST or FOUND") String reportType,
            UUID flatId, @NotBlank @Size(max=40) String itemType,
            @Size(max=120) String itemOther, @NotBlank @Size(max=240) String location,
            @NotNull Instant occurredAt, @NotBlank @Size(max=2000) String description,
            @NotBlank @Size(max=40) String contactPreference, @Size(max=240) String contactOther) { }
    public record Claim(@NotBlank @Size(max=2000) String identifyingDetails) { }
    public record StatusChange(@NotBlank @Pattern(regexp="(?i)UNDER_REVIEW|PUBLISHED|RESOLVED|REJECTED|EXPIRED|CLOSED_UNCLAIMED", message="Status must be a supported lost-and-found report status") String status) { }
    public record FlatAssignment(@NotNull UUID flatId) { }
    public record ClaimDecision(@NotBlank @Pattern(regexp="(?i)ACCEPTED|REJECTED", message="Claim decision must be ACCEPTED or REJECTED") String status) { }
    public record Notice(UUID id, UUID flatId, String reportType, String itemType, String itemOther,
            String location, Instant occurredAt, String description, String status,
            String photoUrl, Instant createdAt) { }
    public record AdminReport(Notice notice, UUID reporterId, String contactPreference,
            String contactOther) { }
    public record AdminClaim(UUID id, UUID reportId, UUID claimantId,
            String identifyingDetails, String status, Instant createdAt) { }
}
