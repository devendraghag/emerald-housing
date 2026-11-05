package com.emerald.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaintenanceStatusToolTest {
    private final SocietyMaintenanceClient client = mock(SocietyMaintenanceClient.class);

    @Test
    void forwardsTheCurrentSessionAndReturnsOnlyTheRequestStatus() {
        UUID requestId = UUID.randomUUID();
        when(client.status(requestId, "current-session"))
                .thenReturn(new SocietyMaintenanceClient.MaintenanceStatus(requestId, "IN_PROGRESS"));
        var tool = new MaintenanceStatusTool(client, "current-session", requestId);

        String result = tool.lookupMaintenanceRequestStatus(requestId.toString());

        assertThat(result).contains(requestId.toString(), "IN_PROGRESS");
        assertThat(tool.wasUsed()).isTrue();
        verify(client).status(requestId, "current-session");
    }

    @Test
    void rejectsMalformedIdsWithoutCallingTheSocietyService() {
        var tool = new MaintenanceStatusTool(client, "current-session", UUID.randomUUID());

        assertThat(tool.lookupMaintenanceRequestStatus("not-a-uuid")).contains("ID is invalid");
        verifyNoInteractions(client);
    }
}
