package com.emerald.ai.chat;

public class AssistantProviderUnavailableException extends RuntimeException {
    public AssistantProviderUnavailableException() {
        super("The assistant's AI provider is unavailable");
    }
}
