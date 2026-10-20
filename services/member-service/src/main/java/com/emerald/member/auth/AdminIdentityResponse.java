package com.emerald.member.auth;
import java.util.UUID;
public record AdminIdentityResponse(UUID adminId,String username,String role,String societyCode,UUID memberId,java.util.Set<UUID> flatIds) {
 public AdminIdentityResponse { flatIds=flatIds==null?java.util.Set.of():java.util.Set.copyOf(flatIds); }
 public AdminIdentityResponse(UUID id,String username,String role) { this(id,username,role,"EMERALD",null,java.util.Set.of()); }
}
