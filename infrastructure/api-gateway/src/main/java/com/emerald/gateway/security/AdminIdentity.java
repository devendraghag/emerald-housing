package com.emerald.gateway.security;
import java.io.Serializable;import java.util.UUID;import java.util.Set;
public record AdminIdentity(UUID adminId,String username,String role,String societyCode,UUID memberId,Set<UUID> flatIds) implements Serializable {
 public AdminIdentity { flatIds=flatIds==null?Set.of():Set.copyOf(flatIds); }
 public AdminIdentity(UUID id,String username) { this(id,username,"ADMIN","EMERALD",null,Set.of()); }
}
