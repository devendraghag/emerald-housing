package com.emerald.ai.tools;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

public class MaintenanceStatusTool {
    private final SocietyMaintenanceClient client;
    private final String sessionId;
    private final UUID suppliedRequestId;
    private final AtomicBoolean used = new AtomicBoolean();

    public MaintenanceStatusTool(SocietyMaintenanceClient client, String sessionId, UUID suppliedRequestId) {
        this.client = client;
        this.sessionId = sessionId;
        this.suppliedRequestId = suppliedRequestId;
    }

    @Tool(description = "Check the current status of one maintenance request belonging to the signed-in resident. Use only when the resident explicitly asks about that request's status and supplies its request ID. Never list requests or guess an ID.")
    public String lookupMaintenanceRequestStatus(
            @ToolParam(description = "The maintenance request UUID supplied by the resident") String requestId) {
        used.set(true);
        final UUID id;
        try {
            id = UUID.fromString(requestId);
        } catch (IllegalArgumentException exception) {
            return "I could not look up that request because its ID is invalid.";
        }
        if (!id.equals(suppliedRequestId)) return "I can only look up the request ID supplied in the resident's question.";
        try {
            var status = client.status(id, sessionId);
            return "Maintenance request " + status.requestId() + " has current status " + status.status() + ".";
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden exception) {
            return "I could not find an accessible maintenance request with that ID.";
        } catch (RestClientException | IllegalStateException exception) {
            return "The maintenance status service is unavailable. Do not guess the request status.";
        }
    }

    public boolean wasUsed() { return used.get(); }
}
