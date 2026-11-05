package com.emerald.ai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AssistantDtos {
    private AssistantDtos() { }

    public record AskRequest(
            @NotBlank @Size(max = 2000) String question,
            @Size(max = 100) String conversationId) { }

    public record Source(String id, String title) { }

    public record AskResponse(String conversationId, String answer, List<Source> sources) {
        public AskResponse { sources = List.copyOf(sources); }
    }
}
