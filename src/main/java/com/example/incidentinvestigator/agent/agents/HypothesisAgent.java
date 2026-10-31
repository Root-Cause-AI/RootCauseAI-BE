package com.example.incidentinvestigator.agent.agents;

import com.example.incidentinvestigator.agent.model.HypothesisItem;
import com.example.incidentinvestigator.agent.model.HypothesisResult;
import com.example.incidentinvestigator.agent.model.InvestigationState;
import com.example.incidentinvestigator.agent.model.LogAnalysisResult;
import com.example.incidentinvestigator.agent.model.RetrievedContextResult;
import com.example.incidentinvestigator.agent.tools.InvestigationTools;
import com.example.incidentinvestigator.agent.trace.AgentTraceRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Specialized agent for evidence evaluation, hypothesis formulation,
 * ranking by supporting vs contradicting evidence, and iterative evidence gathering.
 */
@Component
public class HypothesisAgent {

    private static final Logger log = LoggerFactory.getLogger(HypothesisAgent.class);

    private final ChatClient chatClient;
    private final InvestigationTools tools;
    private final AgentTraceRecorder traceRecorder;
    private final ObjectMapper objectMapper;

    public HypothesisAgent(ChatClient.Builder chatClientBuilder,
                           InvestigationTools tools,
                           AgentTraceRecorder traceRecorder,
                           ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.tools = tools;
        this.traceRecorder = traceRecorder;
        this.objectMapper = objectMapper;
    }

    public HypothesisResult execute(InvestigationState state) {
        long startTime = System.currentTimeMillis();
        var incident = state.getIncident();
        LogAnalysisResult logAnalysis = state.getLogAnalysis();
        RetrievedContextResult context = state.getRetrievedContext();

        log.info("[HypothesisAgent] Formulating hypotheses for incident {}", incident.getId());

        String prompt = buildPrompt(incident.getTitle(),
                logAnalysis, context, null);

        long llmStart = System.currentTimeMillis();
        String response = chatClient.prompt()
                .system("""
                        You are a Principal SRE Root-Cause Hypothesis Specialist.
                        Analyze the log evidence, past incidents, runbooks, and deployment context.
                        Produce evidence-grounded hypotheses with verified facts, supporting evidence,
                        and contradicting evidence.
                        
                        You MUST reply ONLY with a valid JSON object matching this schema:
                        {
                          "rankedHypotheses": [
                            {
                              "hypothesis": "string",
                              "confidence": 85,
                              "supportingEvidence": ["string"],
                              "contradictingEvidence": ["string"],
                              "verifiedFacts": ["string"],
                              "needsMoreEvidence": false,
                              "targetedSearchQuery": null
                            }
                          ],
                          "reasoningSummary": "string"
                        }
                        """)
                .user(prompt)
                .call()
                .content();

        long llmDuration = System.currentTimeMillis() - llmStart;
        HypothesisResult result = parseResponse(response);

        // Check if iterative evidence search is warranted and budget allows
        if (result.getPrimaryHypothesis() != null
                && result.getPrimaryHypothesis().isNeedsMoreEvidence()
                && result.getPrimaryHypothesis().getTargetedSearchQuery() != null
                && state.canIterate()) {

            state.incrementIteration();
            String targetedQuery = result.getPrimaryHypothesis().getTargetedSearchQuery();
            log.info("[HypothesisAgent] Iterative retrieval triggered: query='{}'", targetedQuery);

            long toolStart = System.currentTimeMillis();
            String kbFollowUp = tools.searchKnowledgeBase(targetedQuery);
            long toolDuration = System.currentTimeMillis() - toolStart;
            state.incrementToolCall();

            traceRecorder.recordToolTrace(
                    incident.getId(),
                    state.getJobId(),
                    "ITERATIVE_INVESTIGATION",
                    "HypothesisAgent",
                    "searchKnowledgeBase",
                    "iterative_query=" + targetedQuery,
                    "Iterative retrieval: deeper evidence collection for low-confidence hypothesis",
                    kbFollowUp,
                    null,
                    toolDuration,
                    !kbFollowUp.contains("\"error\"")
            );

            // Re-evaluate with additional evidence
            String iterativePrompt = buildPrompt(incident.getTitle(),
                    logAnalysis, context, kbFollowUp);

            long iterLlmStart = System.currentTimeMillis();
            String iterResponse = chatClient.prompt()
                    .system("You are a Principal SRE. Refine the hypotheses with the new retrieved evidence. Respond in the same JSON format.")
                    .user(iterativePrompt)
                    .call()
                    .content();

            llmDuration += (System.currentTimeMillis() - iterLlmStart);
            result = parseResponse(iterResponse);
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        String summary = result.getPrimaryHypothesis() != null
                ? result.getPrimaryHypothesis().getHypothesis() + " (" + result.getPrimaryHypothesis().getConfidence() + "%)"
                : "Hypotheses generated";

        traceRecorder.recordStageTrace(
                incident.getId(),
                state.getJobId(),
                "HYPOTHESIS_GENERATION",
                "HypothesisAgent",
                summary,
                response,
                totalDuration,
                llmDuration,
                null
        );

        state.setHypothesisResult(result);
        return result;
    }

    private String buildPrompt(String title,
                               LogAnalysisResult logAnalysis,
                               RetrievedContextResult context,
                               String extraEvidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("Incident: ").append(title).append("\n\n");

        if (logAnalysis != null) {
            sb.append("Log Findings:\n");
            sb.append("- Dominant Pattern: ").append(logAnalysis.getRootPattern()).append("\n");
            sb.append("- Affected Component: ").append(logAnalysis.getAffectedComponent()).append("\n");
            sb.append("- Errors: ").append(logAnalysis.getDetectedErrors()).append("\n\n");
        }

        if (context != null) {
            sb.append("Retrieved Context:\n");
            sb.append("- Runbooks: ").append(context.getRunbookTitles()).append("\n");
            sb.append("- Past Incidents: ").append(context.getPastIncidentInsights()).append("\n");
            sb.append("- Deployment Metadata: ").append(context.getDeploymentSummary()).append("\n\n");
        }

        if (extraEvidence != null) {
            sb.append("Additional Iterative Evidence:\n").append(extraEvidence).append("\n\n");
        }

        return sb.toString();
    }

    private HypothesisResult parseResponse(String raw) {
        List<HypothesisItem> items = new ArrayList<>();
        String reasoning = "Hypothesis generation complete.";

        if (raw == null || raw.isBlank()) {
            return HypothesisResult.builder()
                    .rankedHypotheses(items)
                    .reasoningSummary(reasoning)
                    .build();
        }

        try {
            String json = cleanJson(raw);
            JsonNode root = objectMapper.readTree(json);

            if (root.has("reasoningSummary")) {
                reasoning = root.get("reasoningSummary").asText();
            }

            if (root.has("rankedHypotheses") && root.get("rankedHypotheses").isArray()) {
                for (JsonNode n : root.get("rankedHypotheses")) {
                    List<String> sup = new ArrayList<>();
                    List<String> contra = new ArrayList<>();
                    List<String> facts = new ArrayList<>();

                    if (n.has("supportingEvidence")) n.get("supportingEvidence").forEach(e -> sup.add(e.asText()));
                    if (n.has("contradictingEvidence")) n.get("contradictingEvidence").forEach(e -> contra.add(e.asText()));
                    if (n.has("verifiedFacts")) n.get("verifiedFacts").forEach(e -> facts.add(e.asText()));

                    items.add(HypothesisItem.builder()
                            .hypothesis(n.path("hypothesis").asText("Unknown hypothesis"))
                            .confidence(n.path("confidence").asInt(50))
                            .supportingEvidence(sup)
                            .contradictingEvidence(contra)
                            .verifiedFacts(facts)
                            .needsMoreEvidence(n.path("needsMoreEvidence").asBoolean(false))
                            .targetedSearchQuery(n.path("targetedSearchQuery").asText(null))
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("[HypothesisAgent] Failed to parse hypothesis JSON: {}", e.getMessage());
            items.add(HypothesisItem.builder()
                    .hypothesis("Analysis of observed log anomalies and deployment changes")
                    .confidence(60)
                    .supportingEvidence(List.of("Correlated logs and deployment events"))
                    .verifiedFacts(List.of("Incident occurred with reported symptoms"))
                    .build());
        }

        items.sort(Comparator.comparingInt(HypothesisItem::getConfidence).reversed());
        HypothesisItem primary = items.isEmpty() ? null : items.get(0);

        return HypothesisResult.builder()
                .rankedHypotheses(items)
                .primaryHypothesis(primary)
                .reasoningSummary(reasoning)
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
