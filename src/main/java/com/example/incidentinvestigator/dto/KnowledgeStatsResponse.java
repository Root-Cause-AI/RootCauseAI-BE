package com.example.incidentinvestigator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Response DTO for knowledge base analytics/stats.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeStatsResponse {
    private long totalDocuments;
    private long totalChunks;
    private Map<String, Long> documentsByType;
}
