package com.emerald.member.security;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
class DynamicIdentityFilterTest {
 @Test void allEnabledRolesReachSecurityContextAndAreClearedAfterRequest()throws Exception{
  var validator=mock(GatewaySessionValidationClient.class);var request=new MockHttpServletRequest("GET","/api/v1/members");
  var actor=new SessionIdentityResponse(UUID.randomUUID(),"custom","A_CUSTOM","EMERALD",null,Set.of(),Set.of("A_CUSTOM","ADMIN"),Set.of());
  when(validator.validate(request)).thenReturn(Optional.of(actor));var filter=new BackendSecurityContextFilter(validator,mock(SecurityErrorWriter.class));
  filter.doFilter(request,new MockHttpServletResponse(),(req,res)->assertEquals(Set.of("ROLE_A_CUSTOM","ROLE_ADMIN"),SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().map(a->a.getAuthority()).collect(java.util.stream.Collectors.toSet())));
  assertNull(SecurityContextHolder.getContext().getAuthentication());
 }
}
