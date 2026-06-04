package com.example.incidentinvestigator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO for parsing the structured JSON response from the LLM.
 *
 * <p>This maps directly to the JSON schema specified in the SRE
 * analysis prompt. It uses lenient deserialization to handle
 * variations in LLM output formatting.</p>
 *
 * <p>Expected JSON schema from AI:</p>
 * <pre>
 * {
 *   "rootCause": "...",
 *   "confidence": 85,
 *   "severity": "HIGH",
 *   "tags": ["DATABASE", "NETWORK"],
 *   "executiveSummary": "...",
 *   "engineeringSummary": "...",
 *   "timeline": [{"timestamp": "...", "event": "...", "type": "ERROR"}],
 *   "evidence": [{"text": "...", "timestamp": "...", "severity": "ERROR"}],
 *   "recommendations": [{"priority": "HIGH", "action": "..."}],
 *   "similarIncidents": [{"incidentId": "...", "reason": "...", "similarity": "HIGH"}]
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiAnalysisResponse {

    @JsonProperty("rootCause")
    private String rootCause;

    @JsonProperty("confidence")
    private Integer confidence;

    @JsonProperty("severity")
    private String severity;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("executiveSummary")
    private String executiveSummary;

    @JsonProperty("engineeringSummary")
    private String engineeringSummary;

    @JsonProperty("timeline")
    private List<TimelineEvent> timeline;

    @JsonProperty("evidence")
    private List<EvidenceItem> evidence;

    @JsonProperty("recommendations")
    private List<Recommendation> recommendations;

    @JsonProperty("similarIncidents")
    private List<SimilarIncident> similarIncidents;

    @JsonProperty("citations")
    private List<Citation> citations;
}
