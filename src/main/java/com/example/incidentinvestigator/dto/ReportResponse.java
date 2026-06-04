package com.example.incidentinvestigator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for investigation reports.
 *
 * <p>Contains the structured AI analysis output with evidence and
 * recommendations as proper lists (parsed from the JSON stored in
 * the database TEXT columns).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportResponse {

    private UUID id;
    private UUID incidentId;
    private String rootCause;
    private Integer confidenceScore;
    private String severity;
    private List<String> tags;
    private String executiveSummary;
    private String engineeringSummary;
    private List<TimelineEvent> timeline;
    private List<EvidenceItem> evidence;
    private List<Recommendation> recommendations;
    private List<SimilarIncident> similarIncidents;
    private List<Citation> citations;
    private List<KnowledgeSearchResult> retrievedKnowledge;
    private LocalDateTime createdAt;
}
