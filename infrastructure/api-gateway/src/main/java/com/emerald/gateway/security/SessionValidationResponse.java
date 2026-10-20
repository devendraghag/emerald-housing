package com.emerald.gateway.security;
import java.util.UUID;
public record SessionValidationResponse(UUID adminId,String username,String role,String societyCode,UUID memberId,java.util.Set<UUID> flatIds) {
 public SessionValidationResponse { flatIds=flatIds==null?java.util.Set.of():java.util.Set.copyOf(flatIds); }
 public SessionValidationResponse(UUID id,String username,String role) { this(id,username,role,"EMERALD",null,java.util.Set.of()); }
}
