package com.emerald.ai.security;

import java.util.Set;
import java.util.UUID;

public record SessionIdentityResponse(UUID adminId, String username, String role, String societyCode,
                                      UUID memberId, Set<UUID> flatIds, Set<String> roles, Set<String> permissions) {
    public SessionIdentityResponse {
        flatIds = flatIds == null ? Set.of() : Set.copyOf(flatIds);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
