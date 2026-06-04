package com.example.incidentinvestigator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private long totalIncidents;
    private long criticalIncidents;
    private long highIncidents;
    private long mediumIncidents;
    private long lowIncidents;
    private Map<String, Long> tagDistribution;
    // For simplicity, trend over time could be Map<String, Long> representing dates.
    private Map<String, Long> incidentTrend;
    private KnowledgeStatsResponse knowledgeStats;
}
