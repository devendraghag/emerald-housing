package com.emerald.member.security;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
class DynamicAccessTest {
 final MemberAccess access=new MemberAccess();final UUID member=UUID.randomUUID(),flat=UUID.randomUUID();
 void login(String role,Set<String> grants){var actor=new SessionIdentityResponse(UUID.randomUUID(),"custom",role,"EMERALD",member,Set.of(flat),Set.of(role),grants);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,List.of()));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void customRoleUsesGrantsAndSelfScope(){login("NEW_CUSTOM_ROLE",Set.of("MEMBER_VIEW:SELF"));assertTrue(access.member(member));assertFalse(access.member(UUID.randomUUID()));assertFalse(access.staff());assertFalse(access.manage());}
 @Test void roleNamesNeverGrantNetworkIdentitiesImplicitAccess(){login("ADMIN",Set.of());assertFalse(access.read());assertFalse(access.manage());}
 @Test void actionPermissionsRemainIndependent(){login("EDITOR",Set.of("MEMBER_UPDATE:SOCIETY"));assertTrue(access.has("MEMBER_UPDATE","SOCIETY"));assertFalse(access.has("MEMBER_CREATE","SOCIETY"));assertFalse(access.has("USER_ASSIGN_ROLE","SOCIETY"));}
 @Test void malformedNetworkIdentityFailsClosed(){var a=new SessionIdentityResponse(UUID.randomUUID(),"admin","ADMIN","EMERALD",null,null,null,null);assertTrue(a.permissions().isEmpty());assertTrue(a.roles().isEmpty());}
}
