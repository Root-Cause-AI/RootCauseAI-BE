package com.example.incidentinvestigator.knowledge;

import java.util.UUID;

/**
 * Represents a single retrieval result from the knowledge base.
 *
 * @param chunkId       the chunk UUID
 * @param documentId    the parent document UUID
 * @param documentTitle the parent document title
 * @param documentType  the document type (RUNBOOK, INCIDENT, KNOWN_FIX, ARCHITECTURE)
 * @param content       the chunk text content
 * @param relevanceScore the combined relevance score (higher = more relevant)
 */
public record RetrievalResult(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        String documentType,
        String content,
        double relevanceScore
) {}
