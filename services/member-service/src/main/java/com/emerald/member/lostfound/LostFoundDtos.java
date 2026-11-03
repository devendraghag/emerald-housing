package com.emerald.member.lostfound;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class LostFoundDtos {
    private LostFoundDtos() { }
    public record CreateReport(@NotBlank String reportType, UUID flatId, @NotBlank String itemType,
            @Size(max=120) String itemOther, @NotBlank @Size(max=240) String location,
            @NotNull Instant occurredAt, @NotBlank @Size(max=2000) String description,
            @NotBlank String contactPreference, @Size(max=240) String contactOther) { }
    public record Claim(@NotBlank @Size(max=2000) String identifyingDetails) { }
    public record StatusChange(@NotBlank String status) { }
    public record FlatAssignment(@NotNull UUID flatId) { }
    public record ClaimDecision(@NotBlank String status) { }
    public record Notice(UUID id, UUID flatId, String reportType, String itemType, String itemOther,
            String location, Instant occurredAt, String description, String status,
            String photoUrl, Instant createdAt) { }
    public record AdminReport(Notice notice, UUID reporterId, String contactPreference,
            String contactOther) { }
    public record AdminClaim(UUID id, UUID reportId, UUID claimantId,
            String identifyingDetails, String status, Instant createdAt) { }
}
