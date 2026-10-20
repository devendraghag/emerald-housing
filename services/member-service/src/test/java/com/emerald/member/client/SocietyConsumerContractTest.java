package com.emerald.member.client;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
class SocietyConsumerContractTest {
 @Test void consumerParsesTheProviderContractAndMapsNotFound() throws Exception {
  var json=JsonMapper.builder().build();
  try(var stream=getClass().getResourceAsStream("/contracts/member-to-society-flat-v1.json")) {
   assertNotNull(stream);var contract=json.readTree(stream);var body=contract.get("response").get("body");
   var server=new WireMockServer(WireMockConfiguration.options().dynamicPort());server.start();
   try {
    server.stubFor(get(urlEqualTo(contract.get("request").get("path").asString())).willReturn(okJson(json.writeValueAsString(body))));
    server.stubFor(get(urlEqualTo(contract.get("missing").get("path").asString())).willReturn(notFound()));
    var client=new SocietyClient(RestClient.builder().baseUrl(server.baseUrl()).build(),json);
    var actual=client.getFlat(UUID.fromString(body.get("flatId").asString()));
    assertEquals(body.get("flatNumber").asString(),actual.flatNumber());assertEquals("ACTIVE",actual.status());
    assertThrows(com.emerald.member.exception.FlatNotFoundException.class,()->client.getFlat(UUID.fromString("00000000-0000-0000-0000-000000000404")));
   } finally {server.stop();}
  }
 }
}
