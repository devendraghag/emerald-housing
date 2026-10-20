package com.emerald.member.api;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
@Component
public class VersionPreconditions {
 private final boolean required;
 public VersionPreconditions(@Value("${app.http.require-if-match:false}") boolean required) { this.required=required; }
 public static String tag(String type, UUID id, long version) { return Character.toString(34)+type+"-"+id+"-v"+version+Character.toString(34); }
 public void check(String supplied,String type,UUID id,long version) {
  if (supplied == null) { if(required) throw new ApiFailure(428,"PRECONDITION_REQUIRED","Send the current ETag in If-Match"); return; }
  String current=tag(type,id,version);
  if (!supplied.trim().equals("*") && Arrays.stream(supplied.split(",")).map(String::trim).noneMatch(current::equals))
   throw new ApiFailure(412,"PRECONDITION_FAILED","The record changed. Reload it before retrying.");
 }
 public static <T> ResponseEntity<T> read(T body,String tag,String supplied) {
  boolean same=supplied != null && Arrays.stream(supplied.split(",")).map(String::trim).map(v -> v.startsWith("W/") ? v.substring(2) : v).anyMatch(v -> v.equals("*") || v.equals(tag));
  return same ? ResponseEntity.status(304).eTag(tag).header("Cache-Control","private, no-cache").build()
       : ResponseEntity.ok().eTag(tag).header("Cache-Control","private, no-cache").body(body);
 }
}
