package com.emerald.gateway;
import static org.junit.jupiter.api.Assertions.*;
import com.emerald.gateway.security.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class GatewayDynamicAuthenticationTest {
 HttpServer server;String roles;AdminAuthenticationManager manager;
 @BeforeEach void start()throws Exception{
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/internal/auth/credentials",exchange->{
   String body="""
    {"adminId":"%s","username":"custom","role":"A_CUSTOM","societyCode":"EMERALD","memberId":null,"flatIds":[],"roles":%s,"permissions":["MEMBER_VIEW:SOCIETY"]}
    """.formatted(UUID.randomUUID(),roles);
   byte[] data=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,data.length);try(var out=exchange.getResponseBody()){out.write(data);}
  });server.start();manager=new AdminAuthenticationManager("http://127.0.0.1:"+server.getAddress().getPort()+"/internal/auth/credentials","x".repeat(32),Duration.ofSeconds(2),Duration.ofSeconds(2));
 }
 @AfterEach void stop(){server.stop(0);}
 @Test void acceptsCustomPrimaryRoleAndRetainsEveryEnabledRole(){
  roles="[\"A_CUSTOM\",\"ADMIN\"]";var auth=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("custom","fixture-password")).block();
  assertNotNull(auth);assertTrue(auth.isAuthenticated());assertEquals(Set.of("ROLE_A_CUSTOM","ROLE_ADMIN"),auth.getAuthorities().stream().map(a->a.getAuthority()).collect(java.util.stream.Collectors.toSet()));
  assertEquals(Set.of("MEMBER_VIEW:SOCIETY"),((AdminIdentity)auth.getPrincipal()).permissions());
 }
 @Test void noEnabledRolesFailsClosed(){roles="[]";assertThrows(AuthServiceUnavailableException.class,()->manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("custom","fixture-password")).block());}
}
