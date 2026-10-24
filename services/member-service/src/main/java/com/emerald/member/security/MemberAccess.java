package com.emerald.member.security;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
@Component("memberAccess")
public class MemberAccess {
 public SessionIdentityResponse identity() { var auth=SecurityContextHolder.getContext().getAuthentication(); return auth!=null && auth.getPrincipal() instanceof SessionIdentityResponse actor?actor:null; }
 public boolean has(String permission,String scope) { var a=identity();return a!=null && "EMERALD".equals(a.societyCode()) && a.permissions().contains(permission+":"+scope); }
 public boolean read() { return has("MEMBER_VIEW","SOCIETY") || has("MEMBER_VIEW","SELF") || has("MEMBER_VIEW","OWN_FLATS"); }
 public boolean manage() { return has("MEMBER_CREATE","SOCIETY"); }
 public boolean staff() { return has("MEMBER_VIEW","SOCIETY"); }
 public boolean member(UUID id) { var a=identity();return read() && (staff() || id.equals(a.memberId())); }
 public boolean flat(UUID id) { var a=identity();return read() && (staff() || a.flatIds().contains(id)); }
 public boolean resident() { return read() && !staff(); }
 public UUID ownMember() { var a=identity();return a==null?null:a.memberId(); }
 public Set<UUID> ownFlats() { var a=identity();return a==null?Set.of():a.flatIds(); }
}
