package com.example.incidentinvestigator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Result DTO for a knowledge search hit.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeSearchResult {
    private UUID chunkId;
    private UUID documentId;
    private String documentTitle;
    private String documentType;
    private String content;
    private double score;
}
