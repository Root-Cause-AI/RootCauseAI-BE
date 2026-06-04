package com.example.incidentinvestigator.controller;

import com.example.incidentinvestigator.dto.KnowledgeDocumentResponse;
import com.example.incidentinvestigator.dto.KnowledgeSearchResult;
import com.example.incidentinvestigator.dto.KnowledgeStatsResponse;
import com.example.incidentinvestigator.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for knowledge base management.
 *
 * <p>Provides endpoints for uploading documents, listing/searching
 * the knowledge base, and retrieving analytics.</p>
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * Uploads a document to the knowledge base.
     * Triggers the full ingestion pipeline: parse → chunk → embed → persist.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("documentType") String documentType) {
        log.info("POST /api/knowledge/upload - Uploading: {} (type: {})", title, documentType);
        KnowledgeDocumentResponse response = knowledgeService.uploadDocument(file, title, documentType);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all knowledge base documents, optionally filtered by type.
     */
    @GetMapping("/documents")
    public ResponseEntity<List<KnowledgeDocumentResponse>> listDocuments(
            @RequestParam(required = false) String type) {
        log.info("GET /api/knowledge/documents - type: {}", type);
        return ResponseEntity.ok(knowledgeService.listDocuments(type));
    }

    /**
     * Gets a single document by ID.
     */
    @GetMapping("/documents/{id}")
    public ResponseEntity<KnowledgeDocumentResponse> getDocument(@PathVariable UUID id) {
        log.info("GET /api/knowledge/documents/{}", id);
        return ResponseEntity.ok(knowledgeService.getDocument(id));
    }

    /**
     * Deletes a document and all its chunks.
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        log.info("DELETE /api/knowledge/documents/{}", id);
        knowledgeService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns knowledge base statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<KnowledgeStatsResponse> getStats() {
        log.info("GET /api/knowledge/stats");
        return ResponseEntity.ok(knowledgeService.getStats());
    }

    /**
     * Searches knowledge base chunks by query.
     */
    @GetMapping("/search")
    public ResponseEntity<List<KnowledgeSearchResult>> searchChunks(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("GET /api/knowledge/search - query: {}, limit: {}", query, limit);
        return ResponseEntity.ok(knowledgeService.searchChunks(query, limit));
    }
}
