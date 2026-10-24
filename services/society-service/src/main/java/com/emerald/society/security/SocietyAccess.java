package com.emerald.society.security;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
@Component("societyAccess")
public class SocietyAccess {
 public SessionIdentityResponse identity() { var auth=SecurityContextHolder.getContext().getAuthentication(); return auth!=null && auth.getPrincipal() instanceof SessionIdentityResponse actor?actor:null; }
 public boolean has(String permission,String scope) { var a=identity();return a!=null && "EMERALD".equals(a.societyCode()) && a.permissions().contains(permission+":"+scope); }
 public boolean read() { return has("FLAT_VIEW","SOCIETY") || has("FLAT_VIEW","SELF") || has("FLAT_VIEW","OWN_FLATS"); }
 public boolean manage() { return has("FLAT_CREATE","SOCIETY"); }
 public boolean staff() { return has("FLAT_VIEW","SOCIETY"); }
 public boolean member(UUID id) { var a=identity();return read() && (staff() || id.equals(a.memberId())); }
 public boolean flat(UUID id) { var a=identity();return read() && (staff() || a.flatIds().contains(id)); }
 public boolean resident() { return read() && !staff(); }
 public UUID ownMember() { var a=identity();return a==null?null:a.memberId(); }
 public Set<UUID> ownFlats() { var a=identity();return a==null?Set.of():a.flatIds(); }
}
