package com.emerald.member.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidPageRequestException.class)
    public ResponseEntity<ApiError> handleInvalidPage(InvalidPageRequestException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            MemberNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(MembershipNotFoundException.class)
    public ResponseEntity<ApiError> handleMembershipNotFound(MembershipNotFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(InvalidMembershipEndDateException.class)
    public ResponseEntity<ApiError> handleInvalidMembershipEndDate(InvalidMembershipEndDateException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(DuplicateMobileNumberException.class)
    public ResponseEntity<ApiError> handleDuplicate(
            DuplicateMobileNumberException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler({FlatNotFoundException.class})
    public ResponseEntity<ApiError> handleFlatNotFound(FlatNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "FLAT_NOT_FOUND", exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(DuplicateMembershipException.class)
    public ResponseEntity<ApiError> handleDuplicateMembership(DuplicateMembershipException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_MEMBERSHIP", exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(InactiveMemberException.class)
    public ResponseEntity<ApiError> handleInactiveMember(InactiveMemberException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "MEMBER_INACTIVE", exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(FlatNotEligibleException.class)
    public ResponseEntity<ApiError> handleFlatNotEligible(FlatNotEligibleException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "FLAT_NOT_ELIGIBLE", exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(SocietyDependencyException.class)
    public ResponseEntity<ApiError> handleSocietyDependency(SocietyDependencyException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatus());
        return error(status, exception.getCode(), exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "The requested operation conflicts with existing data", Map.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> details = new TreeMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more request fields are invalid", details, request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "The request contains an invalid value or malformed JSON", Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected error while handling {}", request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", Map.of(), request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message,
                                           Map<String, String> details, HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, request.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }
}
