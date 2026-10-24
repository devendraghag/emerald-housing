package com.emerald.society.security;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;

/** Exercises actual HTTP and JSON deserialization rather than constructing principals directly. */
class GatewayDynamicSessionTest {
 HttpServer server;String payload;GatewaySessionValidationClient client;
 @BeforeEach void start()throws Exception{
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/internal/auth/session",exchange->{
   assertEquals("x".repeat(32),exchange.getRequestHeaders().getFirst("X-Internal-Session-Validation-Token"));
   assertEquals("SESSION=test-session",exchange.getRequestHeaders().getFirst("Cookie"));
   byte[] data=payload.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,data.length);try(var out=exchange.getResponseBody()){out.write(data);}
  });server.start();
  client=new GatewaySessionValidationClient("http://127.0.0.1:"+server.getAddress().getPort()+"/internal/auth/session","SESSION","x".repeat(32),Duration.ofSeconds(2),Duration.ofSeconds(2));
 }
 @AfterEach void stop(){server.stop(0);}
 String identity(String role,String society,String roles){return """
  {"adminId":"%s","username":"account","role":"%s","societyCode":"%s","memberId":"%s","flatIds":[],"roles":%s,"permissions":["MEMBER_VIEW:SELF","FLAT_VIEW:OWN_FLATS"]}
  """.formatted(UUID.randomUUID(),role,society,UUID.randomUUID(),roles);}
 MockHttpServletRequest request(){var request=new MockHttpServletRequest();request.setCookies(new Cookie("SESSION","test-session"));return request;}
 @Test void acceptsOwnerAndNewCustomRoleNamesOverHttp(){
  for(String role:java.util.List.of("FLAT_OWNER","SECRETARY_CUSTOM")){
   payload=identity(role,"EMERALD","[\""+role+"\"]");var actor=client.validate(request()).orElseThrow();assertTrue(actor.roles().contains(role));assertTrue(actor.permissions().contains("MEMBER_VIEW:SELF"));
  }
 }
 @Test void rejectsMissingRolesAndWrongSociety(){payload=identity("ADMIN","EMERALD","[]");assertTrue(client.validate(request()).isEmpty());payload=identity("ADMIN","OTHER","[\"ADMIN\"]");assertTrue(client.validate(request()).isEmpty());}
 @Test void noSessionCookieDoesNotAuthenticate(){assertTrue(client.validate(new MockHttpServletRequest()).isEmpty());}
}
