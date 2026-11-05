package com.emerald.ai.chat;

import java.util.Map;
import java.util.Arrays;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class HousingFaqIndex {
    private static final Logger log = LoggerFactory.getLogger(HousingFaqIndex.class);

    @Bean
    ChatMemory assistantChatMemory() {
        return MessageWindowChatMemory.builder().maxMessages(20).build();
    }

    @Bean
    FaqCatalog housingFaqCatalog(ResourceLoader resources, JsonMapper jsonMapper) throws java.io.IOException {
        FaqEntry[] entries = jsonMapper.readValue(resources.getResource("classpath:housing-faqs.json").getInputStream(), FaqEntry[].class);
        return new FaqCatalog(Arrays.asList(entries));
    }

    @Bean
    VectorStore housingFaqVectorStore(EmbeddingModel embeddingModel, FaqCatalog catalog) {
        VectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        try {
            store.add(catalog.entries().stream().map(entry -> new org.springframework.ai.document.Document(
                    entry.id(), entry.content(), Map.of("id", entry.id(), "title", entry.title(), "sourceType", "portal-guide"))).toList());
        } catch (RuntimeException exception) {
            // Embeddings require a live provider call. Keep the service available if that provider
            // is temporarily unavailable or out of credits; FAQ requests will fail gracefully.
            log.warn("Could not index housing FAQs; starting with an empty FAQ vector store. FAQ retrieval will be unavailable until restart after the embedding provider recovers.", exception);
        }
        return store;
    }

    public record FaqCatalog(List<FaqEntry> entries) {
        public FaqCatalog { entries = List.copyOf(entries); }
    }

    public record FaqEntry(String id, String title, String content) { }
}
