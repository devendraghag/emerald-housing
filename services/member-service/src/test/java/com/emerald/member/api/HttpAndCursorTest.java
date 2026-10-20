package com.emerald.member.api;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;import java.util.UUID;
import org.junit.jupiter.api.Test;
class HttpAndCursorTest {
 @Test void conditionalReadsAndWritesUseStrongEntityVersions() {
  var id=UUID.randomUUID();var tag=VersionPreconditions.tag("record",id,2);
  assertEquals(304,VersionPreconditions.read("body",tag,"W/"+tag).getStatusCode().value());
  assertEquals(200,VersionPreconditions.read("body",tag,"\"different\"").getStatusCode().value());
  var checks=new VersionPreconditions(true);
  assertEquals(428,assertThrows(ApiFailure.class,()->checks.check(null,"record",id,2)).status());
  assertEquals(412,assertThrows(ApiFailure.class,()->checks.check(VersionPreconditions.tag("record",id,1),"record",id,2)).status());
  assertEquals(412,assertThrows(ApiFailure.class,()->checks.check("W/"+tag,"record",id,2)).status());
  assertDoesNotThrow(()->checks.check(tag,"record",id,2));
  assertDoesNotThrow(()->new VersionPreconditions(false).check(null,"record",id,2));
 }
 @Test void cursorRejectsMalformedValuesAndChangedQueryScope() {
  var time=Instant.parse("2026-10-20T10:00:00Z");var id=UUID.randomUUID();var scope=CursorCodec.scope("resident-a|ACTIVE");
  var token=CursorCodec.encode(time,id,scope);var decoded=CursorCodec.decode(token,scope);
  assertEquals(time,decoded.createdAt());assertEquals(id,decoded.id());
  assertThrows(ApiFailure.class,()->CursorCodec.decode(token,CursorCodec.scope("resident-b|ACTIVE")));
  assertThrows(ApiFailure.class,()->CursorCodec.decode("bad-token",scope));
  assertThrows(ApiFailure.class,()->CursorCodec.decode("x".repeat(513),scope));
 }
 @Test void filtersAreNormalizedAndValidated() {
  assertEquals("asha",DirectoryQueries.search("  ASHA  "));
  assertEquals("ACTIVE",DirectoryQueries.status(" active "));
  assertThrows(ApiFailure.class,()->DirectoryQueries.status("DELETED"));
  assertThrows(ApiFailure.class,()->DirectoryQueries.search("a".repeat(121)));
 }
}
