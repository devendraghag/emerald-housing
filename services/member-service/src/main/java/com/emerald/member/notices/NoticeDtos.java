package com.emerald.member.notices;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NoticeDtos {
    private NoticeDtos() { }

    public record CreateNotice(@NotBlank @Size(max=160) String title,
            @NotBlank @Size(max=4000) String message,
            @NotBlank @Pattern(regexp="(?i)ADMIN_ONLY|SOCIETY|FLATS", message="Audience must be ADMIN_ONLY, SOCIETY, or FLATS") String audienceScope,
            List<@NotNull UUID> flatIds) { }
    public record AudienceChange(@NotBlank @Pattern(regexp="(?i)ADMIN_ONLY|SOCIETY|FLATS", message="Audience must be ADMIN_ONLY, SOCIETY, or FLATS") String audienceScope,
            List<@NotNull UUID> flatIds) { }
    public record StatusChange(@NotBlank @Pattern(regexp="(?i)UNDER_REVIEW|PUBLISHED|REJECTED|CLOSED|EXPIRED", message="Status must be UNDER_REVIEW, PUBLISHED, REJECTED, CLOSED, or EXPIRED") String status) { }
    public record Notice(UUID id, String title, String message, String audienceScope,
            String status, Instant createdAt) { }
    public record ReviewNotice(Notice notice, UUID createdBy, List<UUID> flatIds) { }
    public record FlatOption(@NotNull UUID flatId, @NotBlank String flatNumber) { }
}
