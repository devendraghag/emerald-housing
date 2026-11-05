package com.emerald.society;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.emerald.society.audit.AuditTrail;
import com.emerald.society.controller.RentalOperationsController;
import com.emerald.society.security.SocietyAccess;
import com.emerald.society.security.SessionIdentityResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class MaintenanceStatusAuthorizationTest {
    private final JdbcTemplate database = mock(JdbcTemplate.class);
    private final UUID requestId = UUID.randomUUID();
    private final UUID flatId = UUID.randomUUID();
    private final RentalOperationsController controller = new RentalOperationsController(
            database, new SocietyAccess(), mock(JsonMapper.class), mock(AuditTrail.class));

    @BeforeEach
    void stubMaintenanceRequest() {
        when(database.query(anyString(), any(RowMapper.class), eq(requestId)))
                .thenReturn(List.<Object[]>of(new Object[]{flatId, "IN_PROGRESS", "{}", 1L}));
    }

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void residentCanReadStatusForAnAuthorizedFlat() {
        authenticate(Set.of(flatId));

        assertThat(controller.maintenanceStatus(requestId))
                .containsEntry("requestId", requestId)
                .containsEntry("status", "IN_PROGRESS");
    }

    @Test
    void residentCannotReadStatusForAnotherFlat() {
        authenticate(Set.of(UUID.randomUUID()));

        assertThrows(AccessDeniedException.class, () -> controller.maintenanceStatus(requestId));
    }

    private void authenticate(Set<UUID> allowedFlats) {
        var actor = new SessionIdentityResponse(UUID.randomUUID(), "resident", "RESIDENT", "EMERALD",
                UUID.randomUUID(), allowedFlats, Set.of("RESIDENT"),
                Set.of("MAINTENANCE_VIEW:OWN_FLATS"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor, null, List.of()));
    }
}
