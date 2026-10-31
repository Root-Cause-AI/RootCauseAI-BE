package com.example.incidentinvestigator.agent.agents;

import com.example.incidentinvestigator.agent.model.InvestigationState;
import com.example.incidentinvestigator.agent.model.LogAnalysisResult;
import com.example.incidentinvestigator.agent.tools.InvestigationTools;
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
 * Specialized agent for deep log inspection, pattern extraction,
 * error signature identification, and initial severity assessment.
 */
@Component
public class LogAnalyzerAgent {

    private static final Logger log = LoggerFactory.getLogger(LogAnalyzerAgent.class);

    private final InvestigationTools tools;
    private final AgentTraceRecorder traceRecorder;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LogAnalyzerAgent(InvestigationTools tools,
                            AgentTraceRecorder traceRecorder,
                            ChatClient.Builder chatClientBuilder,
                            ObjectMapper objectMapper) {
        this.tools = tools;
        this.traceRecorder = traceRecorder;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public LogAnalysisResult execute(InvestigationState state) {
        long startTime = System.currentTimeMillis();
        String logs = state.getIncident().getLogs();
        log.info("[LogAnalyzerAgent] Starting log analysis for incident {}", state.getIncident().getId());

        // Step 1: Execute tool-based pattern extraction
        long toolStart = System.currentTimeMillis();
        String toolJson = tools.analyzeLogPatterns(logs);
        long toolDuration = System.currentTimeMillis() - toolStart;
        state.incrementToolCall();

        traceRecorder.recordToolTrace(
                state.getIncident().getId(),
                state.getJobId(),
                "LOG_ANALYSIS",
                "LogAnalyzerAgent",
                "analyzeLogPatterns",
                "logs_len=" + (logs != null ? logs.length() : 0),
                "Extracted error patterns and regex signatures from logs",
                toolJson,
                null,
                toolDuration,
                !toolJson.contains("\"error\"")
        );

        // Step 2: Parse tool findings
        List<String> detectedErrors = new ArrayList<>();
        String rootPattern = "Unknown error pattern";
        String affectedComponent = "Application Service";
        String severity = "HIGH";

        try {
            JsonNode root = objectMapper.readTree(toolJson);
            JsonNode logAnalysisNode = root.has("logAnalysis") ? root.get("logAnalysis") : root;

            if (logAnalysisNode.has("exceptionTypes") && logAnalysisNode.get("exceptionTypes").isArray()) {
                logAnalysisNode.get("exceptionTypes").forEach(p -> detectedErrors.add(p.asText()));
                if (!detectedErrors.isEmpty()) {
                    rootPattern = detectedErrors.get(0);
                }
            }

            if (logAnalysisNode.has("patterns")) {
                JsonNode pNode = logAnalysisNode.get("patterns");
                if (pNode.path("hasTimeoutErrors").asBoolean(false)) {
                    detectedErrors.add("Connection/Request Timeout");
                    if (rootPattern.equals("Unknown error pattern")) {
                        rootPattern = "Database / Network Connection Timeout";
                    }
                }
                if (pNode.path("hasConnectionRefused").asBoolean(false)) {
                    detectedErrors.add("Connection Refused");
                    if (rootPattern.equals("Unknown error pattern")) {
                        rootPattern = "Connection Refused by Database Host";
                    }
                }
            }

            if (rootPattern.equals("Unknown error pattern") && state.getIncident().getTitle() != null) {
                rootPattern = state.getIncident().getTitle();
            }
        } catch (Exception e) {
            log.warn("[LogAnalyzerAgent] Could not parse tool output JSON: {}", e.getMessage());
            if (state.getIncident().getTitle() != null) {
                rootPattern = state.getIncident().getTitle();
            }
        }

        // Step 3: LLM synthesis of log anomalies
        long llmStart = System.currentTimeMillis();
        long llmLatency = 0;
        String summary = "Log analysis identified key error patterns: " + rootPattern;

        try {
            String prompt = String.format("""
                    Analyze the following error logs and tool extraction results:
                    
                    Tool Findings:
                    - Dominant Pattern: %s
                    - Component: %s
                    - Detected Errors: %s
                    
                    Raw Logs Excerpt:
                    %s
                    
                    Provide a concise 2-sentence technical summary of the log anomalies, root failure signatures, and impact.
                    """,
                    rootPattern,
                    affectedComponent,
                    detectedErrors,
                    logs != null && logs.length() > 2000 ? logs.substring(0, 2000) : logs);

            String response = chatClient.prompt()
                    .system("You are an expert SRE Log Analysis Agent. Be concise, factual, and strictly objective.")
                    .user(prompt)
                    .call()
                    .content();

            llmLatency = System.currentTimeMillis() - llmStart;
            if (response != null && !response.isBlank()) {
                summary = response.trim();
            }
        } catch (Exception e) {
            log.warn("[LogAnalyzerAgent] LLM log synthesis failed, using rule-based summary: {}", e.getMessage());
            llmLatency = System.currentTimeMillis() - llmStart;
        }

        LogAnalysisResult result = LogAnalysisResult.builder()
                .detectedErrors(detectedErrors)
                .rootPattern(rootPattern)
                .affectedComponent(affectedComponent)
                .severity(severity)
                .summary(summary)
                .build();

        long totalDuration = System.currentTimeMillis() - startTime;
        traceRecorder.recordStageTrace(
                state.getIncident().getId(),
                state.getJobId(),
                "LOG_ANALYSIS",
                "LogAnalyzerAgent",
                summary,
                toolJson,
                totalDuration,
                llmLatency,
                null
        );

        state.setLogAnalysis(result);
        return result;
    }
}
