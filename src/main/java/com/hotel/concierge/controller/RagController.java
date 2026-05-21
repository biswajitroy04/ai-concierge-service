package com.hotel.concierge.controller;

import com.hotel.concierge.service.RagIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "RAG", description = "RAG document management - upload, list, and ingest documents into Weaviate")
public class RagController {

    private final RagIngestionService ragIngestionService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document to the vector store")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "general") String category) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".txt") && !fileName.endsWith(".md")
                && !fileName.endsWith(".csv") && !fileName.endsWith(".pdf") && !fileName.endsWith(".docx"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Supported formats: .txt, .md, .csv, .pdf, .docx"));
        }

        Map<String, Object> result = ragIngestionService.ingestFile(file, category);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/ingest-defaults")
    @Operation(summary = "Ingest all default RAG documents from classpath (FAQs, amenities, policies, menus, spa)")
    public ResponseEntity<List<Map<String, Object>>> ingestDefaults() {
        List<Map<String, Object>> results = ragIngestionService.ingestClasspathDocuments();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/documents")
    @Operation(summary = "List all documents uploaded to the vector store")
    public ResponseEntity<List<Map<String, Object>>> listDocuments() {
        return ResponseEntity.ok(ragIngestionService.getUploadedDocuments());
    }

    @GetMapping("/stats")
    @Operation(summary = "Get vector store statistics")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(ragIngestionService.getVectorStoreStats());
    }

    @DeleteMapping("/documents")
    @Operation(summary = "Delete a document and all its chunks from the vector store")
    public ResponseEntity<Map<String, Object>> deleteDocument(@RequestParam String fileName) {
        Map<String, Object> result = ragIngestionService.deleteDocument(fileName);
        if ("SUCCESS".equals(result.get("status"))) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }
}
