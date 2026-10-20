package com.emerald.member.security;
import java.util.UUID;
import java.util.Set;
public record SessionIdentityResponse(UUID adminId,String username,String role,String societyCode,UUID memberId,Set<UUID> flatIds) {
 public SessionIdentityResponse { flatIds=flatIds==null?Set.of():Set.copyOf(flatIds); }
 public SessionIdentityResponse(UUID id,String username,String role) { this(id,username,role,"EMERALD",null,Set.of()); }
}
