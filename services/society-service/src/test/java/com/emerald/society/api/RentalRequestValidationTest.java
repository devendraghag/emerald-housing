package com.emerald.society.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emerald.society.audit.AuditTrail;
import com.emerald.society.controller.RentalOperationsController;
import com.emerald.society.security.SocietyAccess;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class RentalRequestValidationTest {
    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;
    private final RentalOperationsController controller = new RentalOperationsController(
            mock(JdbcTemplate.class), mock(SocietyAccess.class), mock(JsonMapper.class), mock(AuditTrail.class));

    @BeforeAll static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll static void closeValidator() { factory.close(); }

    @Test
    void dynamicRentalBodiesMustBeNonEmptyJsonObjects() throws Exception {
        assertInvalidEmptyMap("createSchedule");
        assertInvalidEmptyMap("create");
        assertTrue(validate("createSchedule", Map.of("flatId", "flat")).isEmpty());
    }

    @Test
    void emptyRentalBodyReturnsStandardValidationErrorWithUsefulDetails() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.emerald.society.exception.GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/v1/rental/schedules").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.body").exists());
    }

    private void assertInvalidEmptyMap(String methodName) throws Exception {
        assertTrue(validate(methodName, Map.of()).stream().anyMatch(v -> v.getMessage().contains("empty")));
    }

    private java.util.Set<jakarta.validation.ConstraintViolation<RentalOperationsController>> validate(
            String methodName, Map<String, Object> body) throws Exception {
        Method method = java.util.Arrays.stream(RentalOperationsController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)
                        && candidate.getParameterCount() > 0
                        && candidate.getParameterTypes()[candidate.getParameterCount() - 1] == Map.class)
                .findFirst().orElseThrow();
        Object[] args = new Object[method.getParameterCount()];
        args[args.length - 1] = body;
        return validator.forExecutables().validateParameters(controller, method, args);
    }
}
