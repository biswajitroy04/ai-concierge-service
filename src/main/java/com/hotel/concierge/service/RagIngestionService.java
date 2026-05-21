package com.hotel.concierge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.weaviate.client.WeaviateClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RagIngestionService {

    private final EmbeddingModel embeddingModel;
    private final WeaviateClient weaviateClient;
    private final ObjectMapper objectMapper;
    private final Tika tika = new Tika();

    @Value("${app.weaviate.class-name}")
    private String className;

    private final List<Map<String, Object>> uploadedDocuments = Collections.synchronizedList(new ArrayList<>());

    public RagIngestionService(EmbeddingModel embeddingModel,
                               WeaviateClient weaviateClient,
                               ObjectMapper objectMapper) {
        this.embeddingModel = embeddingModel;
        this.weaviateClient = weaviateClient;
        this.objectMapper = objectMapper;
    }

    /**
     * On startup, query Weaviate to rebuild the uploaded documents list.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadExistingDocuments() {
        log.info("Loading existing documents from Weaviate...");
        try {
            String query = """
                    { Get { %s(limit: 1000) { fileName category _additional { id } } } }
                    """.formatted(className);

            var result = weaviateClient.graphQL().raw().withQuery(query).run();

            if (result.getResult() != null && result.getResult().getData() != null) {
                JsonNode root = objectMapper.valueToTree(result.getResult().getData());
                JsonNode items = root.path("Get").path(className);

                Map<String, Map<String, Object>> docMap = new LinkedHashMap<>();
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String fileName = item.path("fileName").asText("");
                        if (!fileName.isBlank()) {
                            docMap.computeIfAbsent(fileName, k -> {
                                Map<String, Object> doc = new LinkedHashMap<>();
                                doc.put("fileName", k);
                                doc.put("category", item.path("category").asText("general"));
                                doc.put("chunksCreated", 0);
                                doc.put("status", "SUCCESS");
                                doc.put("uploadedAt", "loaded from Weaviate");
                                return doc;
                            });
                            docMap.get(fileName).put("chunksCreated",
                                    (int) docMap.get(fileName).get("chunksCreated") + 1);
                        }
                    }
                }

                uploadedDocuments.addAll(docMap.values());
                log.info("Loaded {} existing documents from Weaviate ({} total chunks)",
                        docMap.size(), docMap.values().stream().mapToInt(d -> (int) d.get("chunksCreated")).sum());
            }
        } catch (Exception e) {
            log.warn("Could not load existing documents from Weaviate: {}. Starting fresh.", e.getMessage());
        }
    }

    /**
     * Ingest a file uploaded via the UI into Weaviate.
     * Supports .txt, .md, .csv, .pdf, .docx
     */
    public Map<String, Object> ingestFile(MultipartFile file, String category) {
        String fileName = file.getOriginalFilename();
        log.info("Ingesting file: {} (category: {})", fileName, category);

        // Check for duplicate
        if (isAlreadyUploaded(fileName)) {
            log.warn("Duplicate upload attempt: {}", fileName);
            Map<String, Object> duplicate = new LinkedHashMap<>();
            duplicate.put("fileName", fileName);
            duplicate.put("status", "DUPLICATE");
            duplicate.put("error", "Document '" + fileName + "' has already been uploaded.");
            return duplicate;
        }

        try {
            // Extract text content (Tika handles PDF/DOCX, plain read for text files)
            String content = extractText(file);

            int chunksIngested = ingestContent(content, fileName, category);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", fileName);
            result.put("category", category);
            result.put("fileSize", file.getSize());
            result.put("chunksCreated", chunksIngested);
            result.put("uploadedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            result.put("status", "SUCCESS");

            uploadedDocuments.add(result);
            log.info("Successfully ingested {} chunks from {}", chunksIngested, fileName);
            return result;

        } catch (Exception e) {
            log.error("Failed to ingest file {}: {}", fileName, e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("fileName", fileName);
            error.put("status", "FAILED");
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * Extract text from a file. Uses Apache Tika for PDF/DOCX, plain read for text files.
     */
    private String extractText(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        if (fileName != null && (fileName.endsWith(".pdf") || fileName.endsWith(".docx") || fileName.endsWith(".doc"))) {
            log.info("Using Tika to extract text from: {}", fileName);
            return tika.parseToString(file.getInputStream());
        }
        // Plain text files
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * Chunk text, generate embeddings, and store in Weaviate.
     */
    public int ingestContent(String content, String fileName, String category) {
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
        Document document = Document.from(content);
        List<TextSegment> segments = splitter.split(document);

        String uploadTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        int chunkIndex = 0;
        for (TextSegment segment : segments) {
            // Generate embedding
            List<Double> embeddingValues = embeddingModel.embed(segment).content().vectorAsList()
                    .stream().map(Float::doubleValue).toList();

            // Store in Weaviate
            Map<String, Object> props = new HashMap<>();
            props.put("content", segment.text());
            props.put("fileName", fileName != null ? fileName : "unknown");
            props.put("category", category != null ? category : "general");
            props.put("chunkIndex", chunkIndex);
            props.put("uploadTime", uploadTime);

            weaviateClient.data().creator()
                    .withClassName(className)
                    .withID(UUID.randomUUID().toString())
                    .withProperties(props)
                    .withVector(toFloatArray(embeddingValues))
                    .run();

            chunkIndex++;
        }

        return chunkIndex;
    }

    /**
     * Ingest the bundled RAG files from classpath.
     */
    public List<Map<String, Object>> ingestClasspathDocuments() {
        String[][] ragFiles = {
                {"rag/hotel-faqs.txt", "faq"},
                {"rag/amenities.txt", "amenities"},
                {"rag/policies.txt", "policies"},
                {"rag/restaurant-menus.txt", "menu"},
                {"rag/spa-services.txt", "spa"}
        };

        List<Map<String, Object>> results = new ArrayList<>();

        for (String[] fileInfo : ragFiles) {
            String filePath = fileInfo[0];
            String category = fileInfo[1];
            String baseName = extractBaseName(filePath);

            if (isAlreadyUploaded(filePath)) {
                Map<String, Object> skip = new LinkedHashMap<>();
                skip.put("fileName", baseName);
                skip.put("status", "DUPLICATE");
                skip.put("error", "Already uploaded");
                results.add(skip);
                continue;
            }

            try {
                var resource = new org.springframework.core.io.ClassPathResource(filePath);
                if (!resource.exists()) {
                    log.warn("RAG file not found: {}", filePath);
                    continue;
                }

                String content;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    content = reader.lines().collect(Collectors.joining("\n"));
                }

                int chunks = ingestContent(content, baseName, category);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("fileName", baseName);
                result.put("category", category);
                result.put("chunksCreated", chunks);
                result.put("uploadedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                result.put("status", "SUCCESS");

                uploadedDocuments.add(result);
                results.add(result);
                log.info("Ingested {} chunks from {}", chunks, baseName);
            } catch (Exception e) {
                log.error("Failed to ingest {}: {}", filePath, e.getMessage());
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("fileName", baseName);
                error.put("status", "FAILED");
                error.put("error", e.getMessage());
                results.add(error);
            }
        }

        return results;
    }

    /**
     * Delete a document and all its chunks from Weaviate.
     */
    public Map<String, Object> deleteDocument(String fileName) {
        log.info("Deleting document from vector store: {}", fileName);
        try {
            final int pageSize = 200;
            int deletedCount = 0;

            while (true) {
                String query = """
                        { Get { %s(where: {path: ["fileName"], operator: Equal, valueText: "%s"}, limit: %d) { _additional { id } } } }
                        """.formatted(className, fileName, pageSize);

                var result = weaviateClient.graphQL().raw().withQuery(query).run();
                JsonNode root = objectMapper.valueToTree(result.getResult().getData());
                JsonNode items = root.path("Get").path(className);

                List<String> ids = new ArrayList<>();
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String id = item.path("_additional").path("id").asText("");
                        if (!id.isBlank()) ids.add(id);
                    }
                }

                if (ids.isEmpty()) break;

                for (String id : ids) {
                    weaviateClient.data().deleter()
                            .withClassName(className)
                            .withID(id)
                            .run();
                    deletedCount++;
                }

                if (ids.size() < pageSize) break;
            }

            // Remove from local tracking
            uploadedDocuments.removeIf(doc -> fileName.equals(doc.get("fileName")));

            log.info("Deleted {} chunks for document '{}'", deletedCount, fileName);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", fileName);
            result.put("status", "SUCCESS");
            result.put("chunksDeleted", deletedCount);
            result.put("message", "Document '" + fileName + "' and all its chunks have been deleted.");
            return result;

        } catch (Exception e) {
            log.error("Failed to delete document '{}': {}", fileName, e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("fileName", fileName);
            error.put("status", "FAILED");
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * Get list of all uploaded documents.
     */
    public List<Map<String, Object>> getUploadedDocuments() {
        return new ArrayList<>(uploadedDocuments);
    }

    /**
     * Get vector store statistics.
     */
    public Map<String, Object> getVectorStoreStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("className", className);
        stats.put("documentsUploaded", uploadedDocuments.size());
        stats.put("totalChunks", uploadedDocuments.stream()
                .filter(d -> "SUCCESS".equals(d.get("status")))
                .mapToInt(d -> (int) d.getOrDefault("chunksCreated", 0))
                .sum());

        // Get actual count from Weaviate
        try {
            String query = "{ Aggregate { %s { meta { count } } } }".formatted(className);
            var result = weaviateClient.graphQL().raw().withQuery(query).run();
            JsonNode root = objectMapper.valueToTree(result.getResult().getData());
            long count = root.path("Aggregate").path(className).path(0).path("meta").path("count").asLong(0);
            stats.put("weaviateChunkCount", count);
        } catch (Exception e) {
            stats.put("weaviateChunkCount", "unavailable");
        }

        return stats;
    }

    // ============ HELPERS ============

    private boolean isAlreadyUploaded(String fileName) {
        if (fileName == null) return false;
        String baseName = extractBaseName(fileName);
        return uploadedDocuments.stream()
                .anyMatch(doc -> "SUCCESS".equals(doc.get("status"))
                        && baseName.equals(extractBaseName((String) doc.get("fileName"))));
    }

    private String extractBaseName(String fileName) {
        if (fileName == null) return "";
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return lastSlash >= 0 ? fileName.substring(lastSlash + 1) : fileName;
    }

    private Float[] toFloatArray(List<Double> values) {
        Float[] vector = new Float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i).floatValue();
        }
        return vector;
    }
}
