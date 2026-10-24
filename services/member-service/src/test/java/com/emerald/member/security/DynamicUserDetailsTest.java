package com.emerald.member.security;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.emerald.member.auth.*;
class DynamicUserDetailsTest {
 @Test void customRoleCodesIncludingRolePrefixCanAuthenticate(){
  var accounts=mock(AdminAccountRepository.class);
  when(accounts.findByUsername("custom")).thenReturn(Optional.of(new AdminAccountEntity(UUID.randomUUID(),"custom","hash","ROLE_REVIEWER","EMERALD",null)));
  var details=new AdminUserDetailsService(accounts).loadUserByUsername("custom");
  assertTrue(details.isEnabled());assertTrue(details.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ROLE_REVIEWER")));
 }
}
