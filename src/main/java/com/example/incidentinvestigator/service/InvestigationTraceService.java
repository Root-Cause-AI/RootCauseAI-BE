package com.example.incidentinvestigator.service;

import com.example.incidentinvestigator.dto.InvestigationTraceResponse;
import com.example.incidentinvestigator.entity.InvestigationTrace;
import com.example.incidentinvestigator.repository.InvestigationTraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for retrieving persistent investigation execution traces.
 */
@Service
public class InvestigationTraceService {

    private final InvestigationTraceRepository traceRepository;

    public InvestigationTraceService(InvestigationTraceRepository traceRepository) {
        this.traceRepository = traceRepository;
    }

    @Transactional(readOnly = true)
    public List<InvestigationTraceResponse> getTracesByIncident(UUID incidentId) {
        return traceRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InvestigationTraceResponse> getTracesByJob(UUID jobId) {
        return traceRepository.findByJobIdOrderByCreatedAtAsc(jobId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private InvestigationTraceResponse toResponse(InvestigationTrace t) {
        return InvestigationTraceResponse.builder()
                .id(t.getId())
                .jobId(t.getJobId())
                .incidentId(t.getIncidentId())
                .stage(t.getStage())
                .agentName(t.getAgentName())
                .toolName(t.getToolName())
                .toolArguments(t.getToolArguments())
                .summary(t.getSummary())
                .outputData(t.getOutputData())
                .status(t.getStatus())
                .durationMs(t.getDurationMs())
                .llmLatencyMs(t.getLlmLatencyMs())
                .tokensUsed(t.getTokensUsed())
                .errorMessage(t.getErrorMessage())
                .evidenceReferences(t.getEvidenceReferences())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
