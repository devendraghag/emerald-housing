package com.emerald.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.emerald.ai.chat.AssistantDtos.AskRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import com.emerald.ai.tools.SocietyMaintenanceClient;

class HousingFaqAssistantTest {
    private final ChatClient.Builder builder = mock(ChatClient.Builder.class);
    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatMemory memory = mock(ChatMemory.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final SocietyMaintenanceClient maintenanceClient = mock(SocietyMaintenanceClient.class);
    private final HousingFaqIndex.FaqCatalog faqCatalog = new HousingFaqIndex.FaqCatalog(List.of(
            new HousingFaqIndex.FaqEntry("PORTAL-LOST-FOUND-01", "Report a lost or found item",
                    "Residents can create a lost or found report in the Emerald Housing portal. Use the report form to describe the item.")));
    private HousingFaqAssistant assistant;

    @BeforeEach
    void setUp() {
        when(builder.defaultAdvisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        assistant = new HousingFaqAssistant(builder, memory, vectorStore, faqCatalog, 3, 0.3, maintenanceClient);
    }

    @Test
    void saysItDoesNotKnowWhenNoRelevantFaqIsRetrieved() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        var response = assistant.ask("resident-account", new AskRequest("What is my parking bay number?", null));

        assertThat(response.answer()).isEqualTo(HousingFaqAssistant.UNKNOWN_ANSWER);
        assertThat(response.conversationId()).isNotBlank();
        assertThat(response.sources()).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    void answersCoveredFaqLocallyWhenEmbeddingProviderIsUnavailable() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new IllegalStateException("provider unavailable"));

        var response = assistant.ask("resident-account", new AskRequest("How do I report a lost item?", null));

        assertThat(response.answer()).startsWith("Found something, or still looking for it?")
                .contains("Describe the item in a Lost & Found report")
                .contains("reports available to your signed-in account");
        assertThat(response.sources()).containsExactly(new com.emerald.ai.chat.AssistantDtos.Source(
                "PORTAL-LOST-FOUND-01", "Report a lost or found item"));
        verifyNoInteractions(chatClient);
    }

    @Test
    void usesDifferentGroundedLostAndFoundWordingForDifferentQuestionPhrasings() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        var lost = assistant.ask("resident-account", new AskRequest("How do I report a lost item?", null));
        var found = assistant.ask("resident-account", new AskRequest("I found an item, where should I report it?", null));

        assertThat(lost.answer()).isNotEqualTo(found.answer());
        assertThat(lost.sources()).containsExactlyElementsOf(found.sources());
        verifyNoInteractions(chatClient);
    }

    @Test
    void handlesGreetingsAndFarewellsWithoutCallingProviders() {
        var hello = assistant.ask("resident-account", new AskRequest("Good morning!", "chat-1"));
        var howAreYou = assistant.ask("resident-account", new AskRequest("How are you?", "chat-1"));
        var goodbye = assistant.ask("resident-account", new AskRequest("Take care", "chat-1"));
        var goodDay = assistant.ask("resident-account", new AskRequest("Wish you a good day ahead", "chat-1"));

        assertThat(hello.answer()).contains("Good morning");
        assertThat(howAreYou.answer()).contains("ready to help");
        assertThat(goodbye.answer()).contains("Take care");
        assertThat(goodDay.answer()).contains("Wishing you a good day");
        assertThat(hello.sources()).isEmpty();
        verifyNoInteractions(vectorStore, chatClient);
    }
}
