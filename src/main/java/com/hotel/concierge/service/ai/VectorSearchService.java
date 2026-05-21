package com.hotel.concierge.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.weaviate.client.WeaviateClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Performs similarity search against Weaviate vector store for RAG retrieval.
 */
@Service
@Slf4j
public class VectorSearchService {

    private final WeaviateClient weaviateClient;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    @Value("${app.weaviate.class-name}")
    private String className;

    @Value("${app.weaviate.top-k}")
    private int topK;

    public VectorSearchService(WeaviateClient weaviateClient,
                               EmbeddingModel embeddingModel,
                               ObjectMapper objectMapper) {
        this.weaviateClient = weaviateClient;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
    }

    /**
     * Search for relevant document chunks based on the user's query.
     * Returns the text content of the top-k most similar chunks.
     */
    public List<String> searchRelevantChunks(String userQuery) {
        try {
            // Generate embedding for the user query
            List<Double> queryEmbedding = embeddingModel.embed(TextSegment.from(userQuery))
                    .content().vectorAsList()
                    .stream().map(Float::doubleValue).toList();

            String vector = queryEmbedding.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            // GraphQL nearVector query
            String query = """
                    {
                      Get {
                        %s(
                          nearVector: { vector: [%s] }
                          limit: %d
                        ) {
                          content
                          fileName
                          category
                        }
                      }
                    }
                    """.formatted(className, vector, topK);

            var result = weaviateClient.graphQL().raw().withQuery(query).run();

            if (result.getResult() != null && result.getResult().getData() != null) {
                JsonNode root = objectMapper.valueToTree(result.getResult().getData());
                JsonNode items = root.path("Get").path(className);

                List<String> chunks = new ArrayList<>();
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String content = item.path("content").asText("");
                        if (!content.isBlank()) {
                            chunks.add(content);
                        }
                    }
                }

                log.debug("RAG search for '{}' returned {} chunks", userQuery, chunks.size());
                return chunks;
            }
        } catch (Exception e) {
            log.warn("RAG vector search failed: {}. Proceeding without context.", e.getMessage());
        }

        return List.of();
    }

    /**
     * Build a context string from retrieved chunks to inject into the prompt.
     */
    public String buildRagContext(String userQuery) {
        List<String> chunks = searchRelevantChunks(userQuery);
        if (chunks.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\n\nRELEVANT HOTEL KNOWLEDGE (use this to answer the guest's question):\n");
        context.append("---\n");
        for (int i = 0; i < chunks.size(); i++) {
            context.append(chunks.get(i)).append("\n---\n");
        }
        return context.toString();
    }
}
