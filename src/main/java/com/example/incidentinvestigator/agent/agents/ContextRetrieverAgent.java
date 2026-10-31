package com.example.incidentinvestigator.agent.agents;

import com.example.incidentinvestigator.agent.model.InvestigationState;
import com.example.incidentinvestigator.agent.model.LogAnalysisResult;
import com.example.incidentinvestigator.agent.model.RetrievedContextResult;
import com.example.incidentinvestigator.agent.tools.InvestigationTools;
import com.example.incidentinvestigator.agent.trace.AgentTraceRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Specialized agent for retrieving relevant knowledge base runbooks,
 * past incident resolutions, and deployment context.
 */
@Component
public class ContextRetrieverAgent {

    private static final Logger log = LoggerFactory.getLogger(ContextRetrieverAgent.class);

    private final InvestigationTools tools;
    private final AgentTraceRecorder traceRecorder;
    private final ObjectMapper objectMapper;

    public ContextRetrieverAgent(InvestigationTools tools,
                                 AgentTraceRecorder traceRecorder,
                                 ObjectMapper objectMapper) {
        this.tools = tools;
        this.traceRecorder = traceRecorder;
        this.objectMapper = objectMapper;
    }

    public RetrievedContextResult execute(InvestigationState state) {
        long startTime = System.currentTimeMillis();
        var incident = state.getIncident();
        LogAnalysisResult logAnalysis = state.getLogAnalysis();
        log.info("[ContextRetrieverAgent] Starting context retrieval for incident {}", incident.getId());

        List<String> runbookTitles = new ArrayList<>();
        List<String> similarIncidentIds = new ArrayList<>();
        List<String> citations = new ArrayList<>();
        StringBuilder insightsBuilder = new StringBuilder();

        // 1. Tool Call: searchKnowledgeBase
        String searchQuery = (logAnalysis != null && logAnalysis.getRootPattern() != null)
                ? logAnalysis.getRootPattern()
                : incident.getTitle();

        long kbStart = System.currentTimeMillis();
        String kbJson = tools.searchKnowledgeBase(searchQuery);
        long kbDuration = System.currentTimeMillis() - kbStart;
        state.incrementToolCall();

        parseKnowledgeBaseResults(kbJson, runbookTitles, citations);
        traceRecorder.recordToolTrace(
                incident.getId(),
                state.getJobId(),
                "CONTEXT_RETRIEVAL",
                "ContextRetrieverAgent",
                "searchKnowledgeBase",
                "query=" + searchQuery,
                "Searched knowledge base for runbooks and known fixes",
                kbJson,
                String.join(", ", citations),
                kbDuration,
                !kbJson.contains("\"error\"")
        );

        // 2. Tool Call: findSimilarIncidents
        String incidentDesc = incident.getTitle() + ": " + (logAnalysis != null ? logAnalysis.getSummary() : "");
        long simStart = System.currentTimeMillis();
        String simJson = tools.findSimilarIncidents(incidentDesc);
        long simDuration = System.currentTimeMillis() - simStart;
        state.incrementToolCall();

        parseSimilarIncidents(simJson, similarIncidentIds, insightsBuilder);
        traceRecorder.recordToolTrace(
                incident.getId(),
                state.getJobId(),
                "CONTEXT_RETRIEVAL",
                "ContextRetrieverAgent",
                "findSimilarIncidents",
                "desc=" + incidentDesc,
                "Queried past resolved incidents for similar root causes",
                simJson,
                String.join(", ", similarIncidentIds),
                simDuration,
                !simJson.contains("\"error\"")
        );

        // 3. Tool Call: getDeploymentContext
        String deployJson = tools.getDeploymentContext(incident.getDeploymentInfo());
        state.incrementToolCall();
        traceRecorder.recordToolTrace(
                incident.getId(),
                state.getJobId(),
                "CONTEXT_RETRIEVAL",
                "ContextRetrieverAgent",
                "getDeploymentContext",
                "deploymentInfo_len=" + (incident.getDeploymentInfo() != null ? incident.getDeploymentInfo().length() : 0),
                "Parsed deployment, version, and environment change metadata",
                deployJson,
                null,
                5,
                true
        );

        String summary = String.format("Retrieved %d runbooks, %d past incidents, and deployment context.",
                runbookTitles.size(), similarIncidentIds.size());

        RetrievedContextResult result = RetrievedContextResult.builder()
                .runbookTitles(runbookTitles)
                .similarIncidentIds(similarIncidentIds)
                .pastIncidentInsights(insightsBuilder.toString())
                .deploymentSummary(deployJson)
                .citations(citations)
                .summary(summary)
                .build();

        long totalDuration = System.currentTimeMillis() - startTime;
        traceRecorder.recordStageTrace(
                incident.getId(),
                state.getJobId(),
                "CONTEXT_RETRIEVAL",
                "ContextRetrieverAgent",
                summary,
                "Runbooks: " + runbookTitles + " | Past Incidents: " + similarIncidentIds,
                totalDuration,
                0,
                null
        );

        state.setRetrievedContext(result);
        return result;
    }

    private void parseKnowledgeBaseResults(String json, List<String> titles, List<String> citations) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("results")) {
                root.get("results").forEach(node -> {
                    if (node.has("documentTitle")) {
                        String title = node.get("documentTitle").asText();
                        titles.add(title);
                        citations.add(title);
                    }
                });
            }
        } catch (Exception e) {
            log.warn("[ContextRetrieverAgent] Failed to parse KB results: {}", e.getMessage());
        }
    }

    private void parseSimilarIncidents(String json, List<String> ids, StringBuilder insights) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("similarIncidents")) {
                root.get("similarIncidents").forEach(node -> {
                    if (node.has("id")) {
                        ids.add(node.get("id").asText());
                    }
                    if (node.has("rootCause")) {
                        insights.append("Past Root Cause: ").append(node.get("rootCause").asText()).append("; ");
                    }
                });
            }
        } catch (Exception e) {
            log.warn("[ContextRetrieverAgent] Failed to parse similar incidents: {}", e.getMessage());
        }
    }
}
