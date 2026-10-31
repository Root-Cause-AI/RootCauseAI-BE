package com.example.incidentinvestigator.agent.agents;

import com.example.incidentinvestigator.agent.model.HypothesisItem;
import com.example.incidentinvestigator.agent.model.InvestigationState;
import com.example.incidentinvestigator.agent.model.RemediationResult;
import com.example.incidentinvestigator.agent.trace.AgentTraceRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Specialized agent for producing grounded remediation plans,
 * runbook actions, preventive measures, and verification procedures.
 */
@Component
public class RemediationAgent {

    private static final Logger log = LoggerFactory.getLogger(RemediationAgent.class);

    private final ChatClient chatClient;
    private final AgentTraceRecorder traceRecorder;
    private final ObjectMapper objectMapper;

    public RemediationAgent(ChatClient.Builder chatClientBuilder,
                            AgentTraceRecorder traceRecorder,
                            ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.traceRecorder = traceRecorder;
        this.objectMapper = objectMapper;
    }

    public RemediationResult execute(InvestigationState state) {
        long startTime = System.currentTimeMillis();
        var incident = state.getIncident();
        var hypothesisResult = state.getHypothesisResult();
        HypothesisItem primary = hypothesisResult != null ? hypothesisResult.getPrimaryHypothesis() : null;

        log.info("[RemediationAgent] Generating remediation plan for incident {}", incident.getId());

        String prompt = String.format("""
                Incident: %s
                Root Cause Hypothesis: %s (Confidence: %d%%)
                Supporting Evidence: %s
                Verified Facts: %s
                Retrieved Runbooks: %s
                Deployment Metadata: %s
                
                Produce actionable, prioritized remediation guidance.
                You MUST reply ONLY with a valid JSON object matching this schema:
                {
                  "immediateActions": ["step 1", "step 2"],
                  "preventiveMeasures": ["preventive 1", "preventive 2"],
                  "verificationSteps": ["verification 1", "verification 2"],
                  "rollbackPlan": "step-by-step rollback if remediation fails",
                  "summary": "1-sentence executive summary"
                }
                """,
                incident.getTitle(),
                primary != null ? primary.getHypothesis() : "Observed service degradation",
                primary != null ? primary.getConfidence() : 50,
                primary != null ? primary.getSupportingEvidence() : List.of(),
                primary != null ? primary.getVerifiedFacts() : List.of(),
                state.getRetrievedContext() != null ? state.getRetrievedContext().getRunbookTitles() : List.of(),
                state.getRetrievedContext() != null ? state.getRetrievedContext().getDeploymentSummary() : "");

        long llmStart = System.currentTimeMillis();
        String response = chatClient.prompt()
                .system("You are an SRE Incident Commander specializing in remediation runbooks. Never propose destructive commands.")
                .user(prompt)
                .call()
                .content();

        long llmDuration = System.currentTimeMillis() - llmStart;
        RemediationResult result = parseResponse(response);

        long totalDuration = System.currentTimeMillis() - startTime;
        traceRecorder.recordStageTrace(
                incident.getId(),
                state.getJobId(),
                "REMEDIATION_PLANNING",
                "RemediationAgent",
                result.getSummary() != null ? result.getSummary() : "Remediation plan generated",
                response,
                totalDuration,
                llmDuration,
                null
        );

        state.setRemediationResult(result);
        return result;
    }

    private RemediationResult parseResponse(String raw) {
        List<String> immediate = new ArrayList<>();
        List<String> preventive = new ArrayList<>();
        List<String> verify = new ArrayList<>();
        String rollback = "Revert to the last known stable deployment image.";
        String summary = "Immediate mitigation and preventive controls outlined.";

        if (raw == null || raw.isBlank()) {
            return RemediationResult.builder()
                    .immediateActions(immediate)
                    .preventiveMeasures(preventive)
                    .verificationSteps(verify)
                    .rollbackPlan(rollback)
                    .summary(summary)
                    .build();
        }

        try {
            String json = cleanJson(raw);
            JsonNode root = objectMapper.readTree(json);

            if (root.has("immediateActions") && root.get("immediateActions").isArray()) {
                root.get("immediateActions").forEach(n -> immediate.add(n.asText()));
            }
            if (root.has("preventiveMeasures") && root.get("preventiveMeasures").isArray()) {
                root.get("preventiveMeasures").forEach(n -> preventive.add(n.asText()));
            }
            if (root.has("verificationSteps") && root.get("verificationSteps").isArray()) {
                root.get("verificationSteps").forEach(n -> verify.add(n.asText()));
            }
            if (root.has("rollbackPlan") && !root.get("rollbackPlan").isNull()) {
                rollback = root.get("rollbackPlan").asText();
            }
            if (root.has("summary") && !root.get("summary").isNull()) {
                summary = root.get("summary").asText();
            }
        } catch (Exception e) {
            log.warn("[RemediationAgent] Failed to parse remediation JSON: {}", e.getMessage());
            immediate.add("Restart affected service instances and check health endpoints.");
            preventive.add("Add alerting thresholds and health checks.");
            verify.add("Verify HTTP 200 responses on service health endpoints.");
        }

        return RemediationResult.builder()
                .immediateActions(immediate)
                .preventiveMeasures(preventive)
                .verificationSteps(verify)
                .rollbackPlan(rollback)
                .summary(summary)
                .build();
    }

    private String cleanJson(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return raw.substring(start, end + 1).trim();
        }
        return raw.trim();
    }
}
