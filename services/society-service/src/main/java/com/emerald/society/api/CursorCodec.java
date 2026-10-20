package com.emerald.society.api;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
public final class CursorCodec {
 private CursorCodec() { }
 public record Position(Instant createdAt,UUID id) { }
 public static String scope(String value) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
  catch(java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
 }
 public static String encode(Instant time,UUID id,String scope) { return Base64.getUrlEncoder().withoutPadding().encodeToString((time+"|"+id+"|"+scope).getBytes(StandardCharsets.UTF_8)); }
 public static Position decode(String token,String expectedScope) {
  if(token==null) return null;
  try {
   if(token.isBlank() || token.length()>512) throw new IllegalArgumentException();
   String[] parts=new String(Base64.getUrlDecoder().decode(token),StandardCharsets.UTF_8).split("[|]",-1);
   if(parts.length!=3 || !parts[2].equals(expectedScope)) throw new IllegalArgumentException();
   return new Position(Instant.parse(parts[0]),UUID.fromString(parts[1]));
  } catch(RuntimeException error) { throw new ApiFailure(400,"INVALID_CURSOR","Cursor is malformed or belongs to different filters or access scope"); }
 }
}
