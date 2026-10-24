package com.emerald.gateway.security;
import java.util.UUID;
import java.util.Set;
public record AdminIdentity(UUID adminId,String username,String role,String societyCode,UUID memberId,Set<UUID> flatIds,Set<String> roles,Set<String> permissions) implements java.io.Serializable {
 public AdminIdentity { flatIds=flatIds==null?Set.of():Set.copyOf(flatIds); roles=roles==null?Set.of():Set.copyOf(roles); permissions=permissions==null?Set.of():Set.copyOf(permissions); }
 public AdminIdentity(UUID id,String username,String role,String society,UUID member,Set<UUID> flats) { this(id,username,role,society,member,flats,Set.of(role),legacy(role)); }
 public AdminIdentity(UUID id,String username,String role) { this(id,username,role,"EMERALD",null,Set.of()); }
 public AdminIdentity(UUID id,String username) { this(id,username,"ADMIN"); }
 // Compatibility for existing in-process callers; network payloads use the canonical constructor and fail closed.
 private static Set<String> legacy(String role) {
  if(Set.of("ADMIN","SOCIETY_ADMIN").contains(role)) return Set.of("MEMBER_VIEW:SOCIETY","MEMBER_CREATE:SOCIETY","MEMBER_UPDATE:SOCIETY","MEMBER_DEACTIVATE:SOCIETY","FLAT_VIEW:SOCIETY","FLAT_CREATE:SOCIETY","FLAT_UPDATE:SOCIETY","FLAT_DEACTIVATE:SOCIETY","SOCIETY_VIEW:SOCIETY","OWNER_REGISTER:SOCIETY","OWNER_END_OWNERSHIP:SOCIETY","AUDIT_VIEW:SOCIETY");
  if("COMMITTEE".equals(role)) return Set.of("MEMBER_VIEW:SOCIETY","FLAT_VIEW:SOCIETY","SOCIETY_VIEW:SOCIETY");
  if("RESIDENT".equals(role)) return Set.of("MEMBER_VIEW:SELF","FLAT_VIEW:OWN_FLATS","SOCIETY_VIEW:SOCIETY");
  return Set.of();
 }
}
