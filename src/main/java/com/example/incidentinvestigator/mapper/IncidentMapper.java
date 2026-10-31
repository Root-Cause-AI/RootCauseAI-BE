package com.example.incidentinvestigator.mapper;

import com.example.incidentinvestigator.dto.AiAnalysisResponse;
import com.example.incidentinvestigator.dto.Citation;
import com.example.incidentinvestigator.dto.CreateIncidentRequest;
import com.example.incidentinvestigator.dto.EvidenceItem;
import com.example.incidentinvestigator.dto.IncidentResponse;
import com.example.incidentinvestigator.dto.IncidentSummaryResponse;
import com.example.incidentinvestigator.dto.KnowledgeSearchResult;
import com.example.incidentinvestigator.dto.Recommendation;
import com.example.incidentinvestigator.dto.ReportResponse;
import com.example.incidentinvestigator.dto.SimilarIncident;
import com.example.incidentinvestigator.dto.TimelineEvent;
import com.example.incidentinvestigator.entity.Incident;
import com.example.incidentinvestigator.entity.InvestigationReport;
import com.example.incidentinvestigator.knowledge.RetrievalResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapping layer between entities and DTOs.
 *
 * <p>This component centralizes all entity-to-DTO and DTO-to-entity
 * conversions, keeping the service layer free of mapping logic.
 * It also handles JSON serialization/deserialization of the evidence,
 * recommendations, citations, and RAG context fields stored as TEXT
 * in the database.</p>
 */
@Component
public class IncidentMapper {

    private static final Logger log = LoggerFactory.getLogger(IncidentMapper.class);

    private final ObjectMapper objectMapper;

    public IncidentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ────────────────────────────────────────────────────────────────
    // Incident mappings
    // ────────────────────────────────────────────────────────────────

    /**
     * Converts a create request DTO into an Incident entity.
     */
    public Incident toEntity(CreateIncidentRequest request) {
        return Incident.builder()
                .title(request.getTitle())
                .logs(request.getLogs())
                .deploymentInfo(request.getDeploymentInfo())
                .build();
    }

    /**
     * Converts an Incident entity into a full response DTO.
     */
    public IncidentResponse toResponse(Incident incident) {
        return IncidentResponse.builder()
                .id(incident.getId())
                .title(incident.getTitle())
                .logs(incident.getLogs())
                .deploymentInfo(incident.getDeploymentInfo())
                .createdAt(incident.getCreatedAt())
                .hasReport(incident.getReport() != null)
                .build();
    }

    /**
     * Converts an Incident entity into a summary response DTO.
     *
     * @param hasReport whether a report exists for this incident
     */
    public IncidentSummaryResponse toSummaryResponse(Incident incident, boolean hasReport) {
        String severity = null;
        List<String> tags = null;
        
        if (hasReport && incident.getReport() != null) {
            severity = incident.getReport().getSeverity();
            tags = deserializeList(incident.getReport().getTags(), new TypeReference<List<String>>() {});
        }
        
        return IncidentSummaryResponse.builder()
                .id(incident.getId())
                .title(incident.getTitle())
                .createdAt(incident.getCreatedAt())
                .hasReport(hasReport)
                .severity(severity)
                .tags(tags)
                .build();
    }

    // ────────────────────────────────────────────────────────────────
    // Report mappings
    // ────────────────────────────────────────────────────────────────

    /**
     * Converts an InvestigationReport entity into a response DTO.
     * Deserializes JSON fields into typed lists.
     */
    public ReportResponse toReportResponse(InvestigationReport report) {
        // Parse RAG sources from the stored ragContext JSON
        List<KnowledgeSearchResult> ragSources = deserializeList(
                report.getRagContext(), new TypeReference<List<KnowledgeSearchResult>>() {});

        return ReportResponse.builder()
                .id(report.getId())
                .incidentId(report.getIncident().getId())
                .rootCause(report.getRootCause())
                .confidenceScore(report.getConfidenceScore())
                .severity(report.getSeverity())
                .tags(deserializeList(report.getTags(), new TypeReference<List<String>>() {}))
                .executiveSummary(report.getExecutiveSummary())
                .engineeringSummary(report.getEngineeringSummary())
                .timeline(deserializeList(report.getTimeline(), new TypeReference<List<TimelineEvent>>() {}))
                .evidence(deserializeList(report.getEvidence(), new TypeReference<List<EvidenceItem>>() {}))
                .recommendations(deserializeList(report.getRecommendations(), new TypeReference<List<Recommendation>>() {}))
                .similarIncidents(deserializeList(report.getSimilarIncidents(), new TypeReference<List<SimilarIncident>>() {}))
                .citations(deserializeList(report.getCitations(), new TypeReference<List<Citation>>() {}))
                .retrievedKnowledge(ragSources)
                .createdAt(report.getCreatedAt())
                .build();
    }

    /**
     * Builds an InvestigationReport entity from an AI analysis response,
     * including RAG context from the retrieval service.
     *
     * @param incident    the parent incident
     * @param aiResponse  the parsed AI output
     * @param ragContext  the retrieved knowledge context (may be empty)
     */
    public InvestigationReport toReportEntity(Incident incident, AiAnalysisResponse aiResponse,
                                              List<RetrievalResult> ragContext) {
        // Serialize RAG context as KnowledgeSearchResult list for frontend consumption
        String ragContextJson = serializeRagContext(ragContext);

        return InvestigationReport.builder()
                .incident(incident)
                .rootCause(aiResponse.getRootCause())
                .confidenceScore(clampConfidence(aiResponse.getConfidence()))
                .severity(aiResponse.getSeverity())
                .tags(serializeObject(aiResponse.getTags()))
                .executiveSummary(aiResponse.getExecutiveSummary())
                .engineeringSummary(aiResponse.getEngineeringSummary())
                .timeline(serializeObject(aiResponse.getTimeline()))
                .evidence(serializeObject(aiResponse.getEvidence()))
                .recommendations(serializeObject(aiResponse.getRecommendations()))
                .similarIncidents(serializeObject(aiResponse.getSimilarIncidents()))
                .ragContext(ragContextJson)
                .citations(serializeObject(aiResponse.getCitations()))
                .build();
    }

    /**
     * Updates an existing InvestigationReport entity in-place from an AI analysis response
     * and RAG context, preserving its identity and avoiding transient JPA reference issues.
     */
    public InvestigationReport updateReportEntity(InvestigationReport report,
                                                  AiAnalysisResponse aiResponse,
                                                  List<RetrievalResult> ragContext) {
        report.setRootCause(aiResponse.getRootCause());
        report.setConfidenceScore(clampConfidence(aiResponse.getConfidence()));
        report.setSeverity(aiResponse.getSeverity());
        report.setTags(serializeObject(aiResponse.getTags()));
        report.setExecutiveSummary(aiResponse.getExecutiveSummary());
        report.setEngineeringSummary(aiResponse.getEngineeringSummary());
        report.setTimeline(serializeObject(aiResponse.getTimeline()));
        report.setEvidence(serializeObject(aiResponse.getEvidence()));
        report.setRecommendations(serializeObject(aiResponse.getRecommendations()));
        report.setSimilarIncidents(serializeObject(aiResponse.getSimilarIncidents()));
        report.setRagContext(serializeRagContext(ragContext));
        report.setCitations(serializeObject(aiResponse.getCitations()));
        return report;
    }

    /**
     * Backward-compatible overload without RAG context.
     */
    public InvestigationReport toReportEntity(Incident incident, AiAnalysisResponse aiResponse) {
        return toReportEntity(incident, aiResponse, List.of());
    }

    // ────────────────────────────────────────────────────────────────
    // JSON helpers
    // ────────────────────────────────────────────────────────────────

    /**
     * Serializes an object into a JSON string for TEXT storage.
     */
    public String serializeObject(Object obj) {
        if (obj == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * Deserializes a JSON array string from the database into a list of a specific type.
     */
    public <T> List<T> deserializeList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Ensures confidence score stays within the valid 0-100 range.
     */
    private Integer clampConfidence(Integer confidence) {
        if (confidence == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, confidence));
    }

    /**
     * Serializes RAG retrieval results into a JSON array of KnowledgeSearchResult format.
     */
    private String serializeRagContext(List<RetrievalResult> ragContext) {
        if (ragContext == null || ragContext.isEmpty()) {
            return "[]";
        }

        List<KnowledgeSearchResult> results = ragContext.stream()
                .map(r -> KnowledgeSearchResult.builder()
                        .chunkId(r.chunkId())
                        .documentId(r.documentId())
                        .documentTitle(r.documentTitle())
                        .documentType(r.documentType())
                        .content(r.content() != null && r.content().length() > 200
                                ? r.content().substring(0, 200) + "..."
                                : r.content())
                        .score(r.relevanceScore())
                        .build())
                .collect(Collectors.toList());

        return serializeObject(results);
    }

    /**
     * Extracts citations from the AI response stored in the report.
     * Citations are properly serialized into the citations field.
     */
    private List<Citation> deserializeCitations(InvestigationReport report) {
        return deserializeList(report.getCitations(), new TypeReference<List<Citation>>() {});
    }
}
