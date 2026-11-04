package com.emerald.member.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emerald.member.exception.GlobalExceptionHandler;
import com.emerald.member.lostfound.LostFoundDtos;
import com.emerald.member.notices.NoticeDtos;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;

class RequestValidationTest {
    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll static void closeValidator() { factory.close(); }

    @Test
    void noticeAndLostFoundDtoConstraintsRejectInvalidEnumsAndNullListEntries() {
        var notice = new NoticeDtos.CreateNotice("Title", "Message", "EVERYONE", Arrays.asList(UUID.randomUUID(), null));
        var report = new LostFoundDtos.CreateReport("DAMAGED", null, "KEYS", null, "Lobby",
                java.time.Instant.now(), "Found keys", "SOCIETY_OFFICE", null);

        assertTrue(validator.validate(notice).stream().anyMatch(v -> v.getPropertyPath().toString().equals("audienceScope")));
        assertTrue(validator.validate(notice).stream().anyMatch(v -> v.getPropertyPath().toString().equals("flatIds[1].<list element>")));
        assertTrue(validator.validate(report).stream().anyMatch(v -> v.getPropertyPath().toString().equals("reportType")));
    }

    @Test
    void constraintErrorsUseTheStandardApiErrorWithFieldDetails() {
        var violations = validator.validate(new NoticeDtos.StatusChange("UNKNOWN"));
        var response = new GlobalExceptionHandler().handleConstraintViolation(
                new ConstraintViolationException(violations), new MockHttpServletRequest("PATCH", "/api/v1/notices/admin/status"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_FAILED", response.getBody().code());
        assertFalse(response.getBody().details().isEmpty());
        assertTrue(response.getBody().details().containsKey("status"));
    }
}
