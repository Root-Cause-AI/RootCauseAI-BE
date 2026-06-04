package com.example.incidentinvestigator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for a knowledge base document.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeDocumentResponse {
    private UUID id;
    private String title;
    private String documentType;
    private String fileName;
    private String fileType;
    private long chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
