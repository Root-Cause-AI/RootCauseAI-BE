package com.example.incidentinvestigator.service;

import com.example.incidentinvestigator.dto.KnowledgeDocumentResponse;
import com.example.incidentinvestigator.dto.KnowledgeSearchResult;
import com.example.incidentinvestigator.dto.KnowledgeStatsResponse;
import com.example.incidentinvestigator.entity.KnowledgeChunk;
import com.example.incidentinvestigator.entity.KnowledgeDocument;
import com.example.incidentinvestigator.exception.ResourceNotFoundException;
import com.example.incidentinvestigator.knowledge.DocumentParser;
import com.example.incidentinvestigator.knowledge.EmbeddingService;
import com.example.incidentinvestigator.knowledge.RetrievalResult;
import com.example.incidentinvestigator.knowledge.RetrievalService;
import com.example.incidentinvestigator.knowledge.TextChunker;
import com.example.incidentinvestigator.repository.KnowledgeChunkRepository;
import com.example.incidentinvestigator.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the knowledge base ingestion pipeline.
 *
 * <p>Manages document upload, text extraction, chunking, embedding
 * generation, and persistence. Also provides document listing, search,
 * and analytics functionality.</p>
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final DocumentParser documentParser;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final RetrievalService retrievalService;

    public KnowledgeService(KnowledgeDocumentRepository documentRepository,
                            KnowledgeChunkRepository chunkRepository,
                            DocumentParser documentParser,
                            TextChunker textChunker,
                            EmbeddingService embeddingService,
                            RetrievalService retrievalService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.documentParser = documentParser;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.retrievalService = retrievalService;
    }

    /**
     * Processes an uploaded document through the full ingestion pipeline:
     * parse → chunk → embed → persist.
     */
    @Transactional
    public KnowledgeDocumentResponse uploadDocument(MultipartFile file, String title, String documentType) {
        log.info("Starting knowledge ingestion for: {} (type: {})", title, documentType);

        // 1. Detect file type and parse text
        String fileType = documentParser.detectFileType(file.getOriginalFilename());
        String content = documentParser.parse(file, fileType);
        log.info("Extracted {} characters from file", content.length());

        // 2. Persist the document
        KnowledgeDocument document = KnowledgeDocument.builder()
                .title(title)
                .content(content)
                .documentType(documentType.toUpperCase())
                .fileName(file.getOriginalFilename())
                .fileType(fileType)
                .metadata("{}")
                .build();
        document = documentRepository.save(document);
        log.info("Document saved with id: {}", document.getId());

        // 3. Chunk the text
        List<String> chunks = textChunker.chunk(content);
        log.info("Split into {} chunks", chunks.size());

        // 4. Generate embeddings for all chunks
        List<float[]> embeddings = embeddingService.embedAll(chunks);
        log.info("Generated {} embeddings", embeddings.size());

        // 5. Persist chunks with embeddings using native SQL
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            float[] embedding = embeddings.get(i);
            int tokenCount = textChunker.estimateTokenCount(chunkText);

            chunkRepository.insertWithEmbedding(
                    UUID.randomUUID(),
                    document.getId(),
                    i,
                    chunkText,
                    EmbeddingService.toVectorString(embedding),
                    tokenCount
            );
        }
        log.info("Persisted {} chunks with embeddings for document: {}", chunks.size(), document.getId());

        return toDocumentResponse(document, chunks.size());
    }

    /**
     * Lists all documents, optionally filtered by type.
     */
    @Transactional(readOnly = true)
    public List<KnowledgeDocumentResponse> listDocuments(String documentType) {
        List<KnowledgeDocument> docs;
        if (documentType != null && !documentType.isBlank()) {
            docs = documentRepository.findByDocumentTypeOrderByCreatedAtDesc(documentType.toUpperCase());
        } else {
            docs = documentRepository.findAllByOrderByCreatedAtDesc();
        }

        return docs.stream()
                .map(doc -> toDocumentResponse(doc, chunkRepository.countByDocumentId(doc.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Gets a single document by ID.
     */
    @Transactional(readOnly = true)
    public KnowledgeDocumentResponse getDocument(UUID id) {
        KnowledgeDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeDocument", id));
        return toDocumentResponse(doc, chunkRepository.countByDocumentId(doc.getId()));
    }

    /**
     * Deletes a document and all its chunks (cascaded via FK).
     */
    @Transactional
    public void deleteDocument(UUID id) {
        if (!documentRepository.existsById(id)) {
            throw new ResourceNotFoundException("KnowledgeDocument", id);
        }
        log.info("Deleting knowledge document: {}", id);
        documentRepository.deleteById(id);
    }

    /**
     * Returns knowledge base statistics.
     */
    @Transactional(readOnly = true)
    public KnowledgeStatsResponse getStats() {
        long totalDocs = documentRepository.count();
        long totalChunks = chunkRepository.count();

        Map<String, Long> byType = new HashMap<>();
        for (String type : List.of("RUNBOOK", "INCIDENT", "KNOWN_FIX", "ARCHITECTURE")) {
            byType.put(type, documentRepository.countByDocumentType(type));
        }

        return KnowledgeStatsResponse.builder()
                .totalDocuments(totalDocs)
                .totalChunks(totalChunks)
                .documentsByType(byType)
                .build();
    }

    /**
     * Searches documents by title.
     */
    @Transactional(readOnly = true)
    public List<KnowledgeDocumentResponse> searchDocuments(String query) {
        return documentRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(query).stream()
                .map(doc -> toDocumentResponse(doc, chunkRepository.countByDocumentId(doc.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Searches knowledge base chunks using hybrid retrieval.
     */
    @Transactional(readOnly = true)
    public List<KnowledgeSearchResult> searchChunks(String query, int limit) {
        List<RetrievalResult> results = retrievalService.retrieve(query, limit);
        return results.stream()
                .map(r -> KnowledgeSearchResult.builder()
                        .chunkId(r.chunkId())
                        .documentId(r.documentId())
                        .documentTitle(r.documentTitle())
                        .documentType(r.documentType())
                        .content(r.content())
                        .score(r.relevanceScore())
                        .build())
                .collect(Collectors.toList());
    }

    private KnowledgeDocumentResponse toDocumentResponse(KnowledgeDocument doc, long chunkCount) {
        return KnowledgeDocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .documentType(doc.getDocumentType())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .chunkCount(chunkCount)
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
