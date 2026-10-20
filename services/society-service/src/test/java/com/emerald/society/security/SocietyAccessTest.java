package com.emerald.society.security;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
class SocietyAccessTest {
 private final SocietyAccess access=new SocietyAccess();
 private final UUID member=UUID.randomUUID(),flat=UUID.randomUUID();
 private void login(String role,String society) {
  var identity=new SessionIdentityResponse(UUID.randomUUID(),"test",role,society,member,Set.of(flat));
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity,null,List.of()));
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void residentIsLimitedToOwnRecordsAndAssignedFlats() {
  login("RESIDENT","EMERALD");assertTrue(access.read());assertFalse(access.manage());assertFalse(access.staff());
  assertTrue(access.member(member));assertFalse(access.member(UUID.randomUUID()));
  assertTrue(access.flat(flat));assertFalse(access.flat(UUID.randomUUID()));
 }
 @Test void committeeCanReadButCannotManage() { login("COMMITTEE","EMERALD");assertTrue(access.staff());assertFalse(access.manage()); }
 @Test void societyAdministratorCanManage() { login("SOCIETY_ADMIN","EMERALD");assertTrue(access.manage()); }
 @Test void wrongSocietyAndAnonymousAreDenied() { login("ADMIN","OTHER");assertFalse(access.read());assertFalse(access.manage());SecurityContextHolder.clearContext();assertFalse(access.read()); }
}
