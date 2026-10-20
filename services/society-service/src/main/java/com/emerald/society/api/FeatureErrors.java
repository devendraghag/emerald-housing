package com.emerald.society.api;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FeatureErrors {
 @ExceptionHandler(ApiFailure.class)
 public ResponseEntity<?> failure(ApiFailure error, HttpServletRequest request) { return response(error.status(),error.code(),error.getMessage(),request); }
 @ExceptionHandler({org.springframework.orm.ObjectOptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
 public ResponseEntity<?> conflict(Exception error, HttpServletRequest request) {
  boolean conditional = request.getHeader("If-Match") != null;
  return response(conditional ? 412 : 409, conditional ? "PRECONDITION_FAILED" : "VERSION_CONFLICT", "The record changed. Reload it before retrying.",request);
 }
 @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
 public ResponseEntity<?> denied(Exception error, HttpServletRequest request) { return response(403,"FORBIDDEN","You cannot access this operation or record",request); }
 private ResponseEntity<?> response(int status,String code,String message,HttpServletRequest request) {
  return ResponseEntity.status(status).body(Map.of("timestamp",Instant.now().toString(),"status",status,"code",code,"message",message,"path",request.getRequestURI()));
 }
}
