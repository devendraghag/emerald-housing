package com.emerald.society;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import com.emerald.society.controller.FlatController;import com.emerald.society.service.FlatService;import com.emerald.society.dto.FlatResponse;
import com.emerald.society.exception.GlobalExceptionHandler;import com.emerald.society.exception.FlatNotFoundException;
import java.util.UUID;import tools.jackson.databind.json.JsonMapper;
class SocietyProviderContractTest {
 @Test void providerHonorsTheMemberOwnedContractAndConditionalGet() throws Exception {
  var json=JsonMapper.builder().build();
  try(var stream=getClass().getResourceAsStream("/contracts/member-to-society-flat-v1.json")) {
   assertNotNull(stream);var contract=json.readTree(stream);var expected=contract.get("response").get("body");
   var id=UUID.fromString(expected.get("flatId").asString());var service=mock(FlatService.class);
   when(service.getCached(id)).thenReturn(json.treeToValue(expected,FlatResponse.class));
   var missingId=UUID.fromString("00000000-0000-0000-0000-000000000404");when(service.getCached(missingId)).thenThrow(new FlatNotFoundException(missingId));
   var mvc=MockMvcBuilders.standaloneSetup(new FlatController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
   var result=mvc.perform(get(contract.get("request").get("path").asString())).andReturn().getResponse();
   assertEquals(200,result.getStatus());assertEquals(expected,json.readTree(result.getContentAsString()));assertNotNull(result.getHeader("ETag"));
   assertEquals(304,mvc.perform(get(contract.get("request").get("path").asString()).header("If-None-Match",result.getHeader("ETag"))).andReturn().getResponse().getStatus());
   var missing=mvc.perform(get(contract.get("missing").get("path").asString())).andReturn().getResponse();
   assertEquals(404,missing.getStatus());assertEquals(contract.get("missing").get("code").asString(),json.readTree(missing.getContentAsString()).get("code").asString());
  }
 }
}
