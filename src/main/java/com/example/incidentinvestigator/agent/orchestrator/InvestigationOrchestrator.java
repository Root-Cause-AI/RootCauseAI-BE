package com.example.incidentinvestigator.agent.orchestrator;

import com.example.incidentinvestigator.agent.agents.ContextRetrieverAgent;
import com.example.incidentinvestigator.agent.agents.HypothesisAgent;
import com.example.incidentinvestigator.agent.agents.LogAnalyzerAgent;
import com.example.incidentinvestigator.agent.agents.RemediationAgent;
import com.example.incidentinvestigator.agent.model.HypothesisItem;
import com.example.incidentinvestigator.agent.model.InvestigationState;
import com.example.incidentinvestigator.agent.trace.AgentTraceRecorder;
import com.example.incidentinvestigator.dto.AiAnalysisResponse;
import com.example.incidentinvestigator.entity.Incident;
import com.example.incidentinvestigator.repository.InvestigationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates multi-agent incident investigations.
 *
 * <p>Executes 4 sequential, state-sharing agent stages:</p>
 * <ol>
 *   <li>Log Analysis (pattern extraction, regex anomaly detection)</li>
 *   <li>Context Retrieval (runbooks, past incident correlation, deployment metadata)</li>
 *   <li>Hypothesis Generation (evidence evaluation, ranking, iterative checks)</li>
 *   <li>Remediation Planning (actionable runbooks, verification, rollback)</li>
 * </ol>
 */
@Service
public class InvestigationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InvestigationOrchestrator.class);

    private final LogAnalyzerAgent logAnalyzerAgent;
    private final ContextRetrieverAgent contextRetrieverAgent;
    private final HypothesisAgent hypothesisAgent;
    private final RemediationAgent remediationAgent;
    private final AgentTraceRecorder traceRecorder;
    private final InvestigationJobRepository jobRepository;

    public InvestigationOrchestrator(LogAnalyzerAgent logAnalyzerAgent,
                                     ContextRetrieverAgent contextRetrieverAgent,
                                     HypothesisAgent hypothesisAgent,
                                     RemediationAgent remediationAgent,
                                     AgentTraceRecorder traceRecorder,
                                     InvestigationJobRepository jobRepository) {
        this.logAnalyzerAgent = logAnalyzerAgent;
        this.contextRetrieverAgent = contextRetrieverAgent;
        this.hypothesisAgent = hypothesisAgent;
        this.remediationAgent = remediationAgent;
        this.traceRecorder = traceRecorder;
        this.jobRepository = jobRepository;
    }

    public AiAnalysisResponse orchestrate(Incident incident, UUID jobId) {
        log.info("Starting orchestrated multi-agent investigation for incident: {} (job: {})",
                incident.getId(), jobId);

        InvestigationState state = InvestigationState.builder()
                .incident(incident)
                .jobId(jobId)
                .build();

        try {
            // Stage 1: Log Analysis
            updateJobStage(jobId, "ANALYZING_LOGS");
            logAnalyzerAgent.execute(state);

            // Stage 2: Context Retrieval
            updateJobStage(jobId, "RETRIEVING_CONTEXT");
            contextRetrieverAgent.execute(state);

            // Stage 3: Hypothesis Formulation & Evidence Evaluation
            updateJobStage(jobId, "FORMULATING_HYPOTHESIS");
            hypothesisAgent.execute(state);

            // Stage 4: Remediation Planning
            updateJobStage(jobId, "REMEDIATION_PLANNING");
            remediationAgent.execute(state);

            return buildFinalAnalysisResponse(state);

        } catch (Exception e) {
            log.error("Investigation orchestration encountered an error: {}", e.getMessage(), e);
            traceRecorder.recordErrorTrace(
                    incident.getId(),
                    jobId,
                    "ORCHESTRATION_ERROR",
                    "InvestigationOrchestrator",
                    "Orchestration failure: " + e.getMessage(),
                    System.currentTimeMillis() - state.getStartTimeMs()
            );
            // Fall back to partial state synthesis if available
            return buildFallbackResponse(incident, state, e);
        }
    }

    private void updateJobStage(UUID jobId, String stage) {
        if (jobId == null) return;
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStage(stage);
            jobRepository.save(job);
        });
    }

    private AiAnalysisResponse buildFinalAnalysisResponse(InvestigationState state) {
        HypothesisItem primary = state.getHypothesisResult() != null
                ? state.getHypothesisResult().getPrimaryHypothesis()
                : null;

        String rootCause = (primary != null && primary.getHypothesis() != null && !primary.getHypothesis().toLowerCase().contains("unknown"))
                ? primary.getHypothesis()
                : (state.getLogAnalysis() != null && state.getLogAnalysis().getRootPattern() != null && !state.getLogAnalysis().getRootPattern().toLowerCase().contains("unknown")
                    ? state.getLogAnalysis().getRootPattern()
                    : state.getIncident().getTitle());

        int confidence = primary != null ? primary.getConfidence() : 65;

        // Evidence synthesis
        List<com.example.incidentinvestigator.dto.EvidenceItem> evidenceItems = new ArrayList<>();
        if (primary != null) {
            for (String fact : primary.getVerifiedFacts()) {
                evidenceItems.add(com.example.incidentinvestigator.dto.EvidenceItem.builder()
                        .text("Verified Fact: " + fact)
                        .severity("INFO")
                        .build());
            }
            for (String sup : primary.getSupportingEvidence()) {
                evidenceItems.add(com.example.incidentinvestigator.dto.EvidenceItem.builder()
                        .text("Supporting Evidence: " + sup)
                        .severity("HIGH")
                        .build());
            }
            for (String contra : primary.getContradictingEvidence()) {
                evidenceItems.add(com.example.incidentinvestigator.dto.EvidenceItem.builder()
                        .text("Ruled Out: " + contra)
                        .severity("LOW")
                        .build());
            }
        }
        if (evidenceItems.isEmpty()) {
            evidenceItems.add(com.example.incidentinvestigator.dto.EvidenceItem.builder()
                    .text("Evidence extracted from application logs: " + rootCause)
                    .severity("MEDIUM")
                    .build());
        }

        // Recommendations synthesis
        List<com.example.incidentinvestigator.dto.Recommendation> recommendations = new ArrayList<>();
        if (state.getRemediationResult() != null) {
            for (String imm : state.getRemediationResult().getImmediateActions()) {
                recommendations.add(com.example.incidentinvestigator.dto.Recommendation.builder()
                        .priority("CRITICAL")
                        .action(imm)
                        .build());
            }
            for (String prev : state.getRemediationResult().getPreventiveMeasures()) {
                recommendations.add(com.example.incidentinvestigator.dto.Recommendation.builder()
                        .priority("MEDIUM")
                        .action(prev)
                        .build());
            }
            if (state.getRemediationResult().getRollbackPlan() != null) {
                recommendations.add(com.example.incidentinvestigator.dto.Recommendation.builder()
                        .priority("HIGH")
                        .action("Rollback: " + state.getRemediationResult().getRollbackPlan())
                        .build());
            }
        }
        if (recommendations.isEmpty()) {
            recommendations.add(com.example.incidentinvestigator.dto.Recommendation.builder()
                    .priority("HIGH")
                    .action("Inspect active instances and verify service health.")
                    .build());
        }

        // Citations synthesis
        List<com.example.incidentinvestigator.dto.Citation> citations = new ArrayList<>();
        if (state.getRetrievedContext() != null && !state.getRetrievedContext().getCitations().isEmpty()) {
            for (String c : state.getRetrievedContext().getCitations()) {
                citations.add(com.example.incidentinvestigator.dto.Citation.builder()
                        .documentTitle(c)
                        .documentType("RUNBOOK")
                        .excerpt("Retrieved via semantic knowledge base search.")
                        .relevance("HIGH")
                        .build());
            }
        }

        String execSummary = state.getRemediationResult() != null && state.getRemediationResult().getSummary() != null
                ? state.getRemediationResult().getSummary()
                : "Incident investigated: " + rootCause;

        String engSummary = state.getHypothesisResult() != null && state.getHypothesisResult().getReasoningSummary() != null
                ? state.getHypothesisResult().getReasoningSummary()
                : "Multi-agent analysis evaluated logs, deployment context, and runbooks.";

        String severity = state.getLogAnalysis() != null && state.getLogAnalysis().getSeverity() != null
                ? state.getLogAnalysis().getSeverity()
                : "HIGH";

        List<String> tags = new ArrayList<>(List.of("AGENTIC", "AUTONOMOUS"));
        if (state.getLogAnalysis() != null && state.getLogAnalysis().getAffectedComponent() != null) {
            tags.add(state.getLogAnalysis().getAffectedComponent().toUpperCase().replace(" ", "_"));
        }

        return AiAnalysisResponse.builder()
                .rootCause(rootCause)
                .confidence(confidence)
                .severity(severity)
                .tags(tags)
                .executiveSummary(execSummary)
                .engineeringSummary(engSummary)
                .evidence(evidenceItems)
                .recommendations(recommendations)
                .citations(citations)
                .build();
    }

    private AiAnalysisResponse buildFallbackResponse(Incident incident, InvestigationState state, Exception e) {
        String rootCause = (state.getLogAnalysis() != null && state.getLogAnalysis().getRootPattern() != null)
                ? state.getLogAnalysis().getRootPattern()
                : "Incident triage in progress: " + incident.getTitle();

        return AiAnalysisResponse.builder()
                .rootCause(rootCause)
                .confidence(50)
                .severity("HIGH")
                .tags(List.of("FALLBACK", "INCIDENT"))
                .executiveSummary("Investigation completed with fallback due to service warning.")
                .engineeringSummary("Partial analysis prior to interruption: " + e.getMessage())
                .evidence(List.of(com.example.incidentinvestigator.dto.EvidenceItem.builder()
                        .text("Partial analysis collected prior to interruption: " + e.getMessage())
                        .severity("MEDIUM")
                        .build()))
                .recommendations(List.of(com.example.incidentinvestigator.dto.Recommendation.builder()
                        .priority("HIGH")
                        .action("Review service logs and verify database connectivity.")
                        .build()))
                .citations(List.of())
                .build();
    }
}
