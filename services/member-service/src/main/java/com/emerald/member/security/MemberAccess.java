package com.emerald.member.security;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
@Component("memberAccess")
public class MemberAccess {
 public SessionIdentityResponse identity() {
  var auth=SecurityContextHolder.getContext().getAuthentication();
  return auth!=null && auth.getPrincipal() instanceof SessionIdentityResponse actor ? actor : null;
 }
 public boolean read() { var a=identity(); return a!=null && "EMERALD".equals(a.societyCode()) && Set.of("ADMIN","SOCIETY_ADMIN","COMMITTEE","RESIDENT").contains(a.role()); }
 public boolean manage() { var a=identity(); return read() && Set.of("ADMIN","SOCIETY_ADMIN").contains(a.role()); }
 public boolean staff() { var a=identity(); return read() && !"RESIDENT".equals(a.role()); }
 public boolean member(UUID id) { var a=identity(); return read() && (staff() || id.equals(a.memberId())); }
 public boolean flat(UUID id) { var a=identity(); return read() && (staff() || a.flatIds().contains(id)); }
 public boolean resident() { var a=identity(); return read() && "RESIDENT".equals(a.role()); }
 public UUID ownMember() { var a=identity(); return a==null?null:a.memberId(); }
 public Set<UUID> ownFlats() { var a=identity(); return a==null?Set.of():a.flatIds(); }
}
