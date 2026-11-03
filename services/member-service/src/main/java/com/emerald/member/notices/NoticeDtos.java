package com.emerald.member.notices;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NoticeDtos {
    private NoticeDtos() { }

    public record CreateNotice(@NotBlank @Size(max=160) String title,
            @NotBlank @Size(max=4000) String message,
            @NotBlank String audienceScope, List<UUID> flatIds) { }
    public record AudienceChange(@NotBlank String audienceScope, List<UUID> flatIds) { }
    public record StatusChange(@NotBlank String status) { }
    public record Notice(UUID id, String title, String message, String audienceScope,
            String status, Instant createdAt) { }
    public record ReviewNotice(Notice notice, UUID createdBy, List<UUID> flatIds) { }
    public record FlatOption(@NotNull UUID flatId, @NotBlank String flatNumber) { }
}
