package com.example.incidentinvestigator.agent.tools;

import com.example.incidentinvestigator.knowledge.RetrievalResult;
import com.example.incidentinvestigator.knowledge.RetrievalService;
import com.example.incidentinvestigator.repository.IncidentRepository;
import com.example.incidentinvestigator.repository.InvestigationReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Spring AI tool definitions for the agentic investigation workflow.
 *
 * <p>Each public method annotated with {@link Tool} is registered as a
 * callable tool with the LLM. The model can dynamically invoke these
 * tools based on investigation context and evidence requirements.</p>
 *
 * <p>Tools reuse existing services — no business logic is duplicated.</p>
 */
@Component
public class InvestigationTools {

    private static final Logger log = LoggerFactory.getLogger(InvestigationTools.class);

    private final RetrievalService retrievalService;
    private final IncidentRepository incidentRepository;
    private final InvestigationReportRepository reportRepository;

    public InvestigationTools(RetrievalService retrievalService,
                               IncidentRepository incidentRepository,
                               InvestigationReportRepository reportRepository) {
        this.retrievalService = retrievalService;
        this.incidentRepository = incidentRepository;
        this.reportRepository = reportRepository;
    }

    /**
     * Searches the knowledge base for documents relevant to a query.
     * Returns up to 5 ranked results with their content excerpts.
     *
     * @param query a specific technical question or error term to look up
     * @return JSON array of matching knowledge base chunks
     */
    @Tool(description = """
            Searches the knowledge base for runbooks, past incident write-ups, and known fixes
            relevant to a specific error, component, or symptom. Use this tool when you need
            additional context about how to handle a specific error pattern, database issue,
            or deployment problem. Returns up to 5 relevant knowledge chunks.
            """)
    public String searchKnowledgeBase(String query) {
        if (query == null || query.isBlank()) {
            return "{\"error\": \"Query must not be empty\"}";
        }
        String sanitizedQuery = query.length() > 500 ? query.substring(0, 500) : query;
        log.info("[Tool] searchKnowledgeBase: query='{}...'", sanitizedQuery.substring(0, Math.min(50, sanitizedQuery.length())));

        try {
            List<RetrievalResult> results = retrievalService.retrieve(sanitizedQuery, 5);
            if (results.isEmpty()) {
                return "{\"results\": [], \"message\": \"No relevant knowledge base documents found.\"}";
            }

            StringBuilder sb = new StringBuilder("{\"results\": [");
            for (int i = 0; i < results.size(); i++) {
                RetrievalResult r = results.get(i);
                String content = r.content() != null && r.content().length() > 300
                        ? r.content().substring(0, 300) + "..."
                        : r.content();
                sb.append(String.format(
                        "{\"documentTitle\":\"%s\",\"documentType\":\"%s\",\"content\":\"%s\",\"relevanceScore\":%.4f}",
                        escapeJson(r.documentTitle()),
                        escapeJson(r.documentType()),
                        escapeJson(content),
                        r.relevanceScore()
                ));
                if (i < results.size() - 1) sb.append(",");
            }
            sb.append("]}");
            return sb.toString();
        } catch (Exception e) {
            log.error("[Tool] searchKnowledgeBase failed: {}", e.getMessage());
            return "{\"error\": \"Knowledge base search failed: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * Finds similar past incidents based on a description of the current incident.
     * Returns up to 3 similar incidents with their root causes.
     *
     * @param incidentDescription a description of the current incident symptoms
     * @return JSON array of similar past incidents
     */
    @Tool(description = """
            Searches for past incidents that are similar to the current incident based on
            symptoms, error patterns, or affected components. Use this when you want to
            check if this incident has occurred before and what resolution worked.
            Returns up to 3 similar incidents with their root causes.
            """)
    public String findSimilarIncidents(String incidentDescription) {
        if (incidentDescription == null || incidentDescription.isBlank()) {
            return "{\"error\": \"Incident description must not be empty\"}";
        }
        String sanitized = incidentDescription.length() > 300 ? incidentDescription.substring(0, 300) : incidentDescription;
        log.info("[Tool] findSimilarIncidents: description='{}...'", sanitized.substring(0, Math.min(60, sanitized.length())));

        try {
            List<RetrievalResult> results = retrievalService.retrieve(sanitized, 3);
            // Filter to INCIDENT type documents only
            List<RetrievalResult> incidents = results.stream()
                    .filter(r -> "INCIDENT".equalsIgnoreCase(r.documentType()))
                    .collect(Collectors.toList());

            if (incidents.isEmpty()) {
                // Fallback: use top results from recent reports
                var recentReports = reportRepository.findTop5ByOrderByCreatedAtDesc();
                if (recentReports.isEmpty()) {
                    return "{\"incidents\": [], \"message\": \"No similar past incidents found.\"}";
                }
                StringBuilder sb = new StringBuilder("{\"incidents\": [");
                for (int i = 0; i < Math.min(3, recentReports.size()); i++) {
                    var report = recentReports.get(i);
                    sb.append(String.format(
                            "{\"incidentTitle\":\"%s\",\"rootCause\":\"%s\",\"severity\":\"%s\"}",
                            escapeJson(report.getIncident() != null ? report.getIncident().getTitle() : "Unknown"),
                            escapeJson(report.getRootCause()),
                            escapeJson(report.getSeverity())
                    ));
                    if (i < Math.min(3, recentReports.size()) - 1) sb.append(",");
                }
                sb.append("]}");
                return sb.toString();
            }

            StringBuilder sb = new StringBuilder("{\"incidents\": [");
            for (int i = 0; i < incidents.size(); i++) {
                RetrievalResult r = incidents.get(i);
                String content = r.content() != null && r.content().length() > 200
                        ? r.content().substring(0, 200) + "..."
                        : r.content();
                sb.append(String.format(
                        "{\"documentTitle\":\"%s\",\"content\":\"%s\",\"relevanceScore\":%.4f}",
                        escapeJson(r.documentTitle()),
                        escapeJson(content),
                        r.relevanceScore()
                ));
                if (i < incidents.size() - 1) sb.append(",");
            }
            sb.append("]}");
            return sb.toString();
        } catch (Exception e) {
            log.error("[Tool] findSimilarIncidents failed: {}", e.getMessage());
            return "{\"error\": \"Similar incident search failed: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * Analyzes log content for common error patterns, exceptions, and anomalies.
     * Extracts error counts, stack trace signatures, and repeated patterns.
     *
     * @param logs the raw application log content to analyze
     * @return JSON object with extracted patterns and metrics
     */
    @Tool(description = """
            Analyzes raw application logs to extract error patterns, exception counts,
            repeated warnings, and anomalies. Use this tool to get a structured breakdown
            of what errors appear in the logs before forming hypotheses. Returns error
            frequency, exception types, and notable patterns found in the logs.
            """)
    public String analyzeLogPatterns(String logs) {
        if (logs == null || logs.isBlank()) {
            return "{\"patterns\": [], \"message\": \"No logs provided for analysis\"}";
        }

        // Limit log content for analysis
        String logContent = logs.length() > 5000 ? logs.substring(0, 5000) : logs;
        log.info("[Tool] analyzeLogPatterns: analyzing {} chars of logs", logContent.length());

        try {
            // Count error/warn occurrences
            long errorCount = countMatches(logContent, "(?i)\\bERROR\\b");
            long warnCount = countMatches(logContent, "(?i)\\bWARN\\b");
            long exceptionCount = countMatches(logContent, "(?i)Exception");

            // Extract distinct exception class names
            List<String> exceptionTypes = extractPattern(logContent,
                    "[A-Za-z]+Exception(?::[^\\n]+)?", 10);

            // Extract stack trace indicators
            List<String> stackTraceLines = extractPattern(logContent,
                    "\\tat [a-zA-Z][\\w.]+\\([^)]+\\)", 5);

            // Extract timeout/connection patterns
            boolean hasTimeout = logContent.toLowerCase().contains("timeout");
            boolean hasConnectionRefused = logContent.toLowerCase().contains("connection refused");
            boolean hasOom = logContent.toLowerCase().contains("outofmemory") ||
                            logContent.toLowerCase().contains("out of memory");
            boolean hasNpe = logContent.toLowerCase().contains("nullpointerexception");

            return String.format("""
                    {"logAnalysis": {
                      "errorCount": %d,
                      "warningCount": %d,
                      "exceptionCount": %d,
                      "exceptionTypes": %s,
                      "sampleStackTraces": %s,
                      "patterns": {
                        "hasTimeoutErrors": %b,
                        "hasConnectionRefused": %b,
                        "hasOutOfMemory": %b,
                        "hasNullPointerException": %b
                      },
                      "analysisNote": "Extracted from first 5000 chars of logs"
                    }}""",
                    errorCount, warnCount, exceptionCount,
                    toJsonArray(exceptionTypes),
                    toJsonArray(stackTraceLines),
                    hasTimeout, hasConnectionRefused, hasOom, hasNpe);

        } catch (Exception e) {
            log.error("[Tool] analyzeLogPatterns failed: {}", e.getMessage());
            return "{\"error\": \"Log pattern analysis failed: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * Retrieves deployment context information for an incident.
     * Returns services deployed, configuration changes, and environment details.
     *
     * @param deploymentInfo the raw deployment information text
     * @return JSON object with structured deployment context
     */
    @Tool(description = """
            Extracts structured deployment context from raw deployment information text.
            Use this tool to understand what was deployed recently, what configuration
            changed, and what environment the incident occurred in. Returns services,
            versions, configuration changes, and timing information.
            """)
    public String getDeploymentContext(String deploymentInfo) {
        if (deploymentInfo == null || deploymentInfo.isBlank()) {
            return "{\"deploymentContext\": null, \"message\": \"No deployment information provided\"}";
        }

        String content = deploymentInfo.length() > 2000 ? deploymentInfo.substring(0, 2000) : deploymentInfo;
        log.info("[Tool] getDeploymentContext: analyzing {} chars of deployment info", content.length());

        try {
            // Detect if a deployment recently occurred
            boolean hasDeployment = content.toLowerCase().contains("deploy") ||
                                    content.toLowerCase().contains("release") ||
                                    content.toLowerCase().contains("rollout");

            // Extract version indicators
            List<String> versions = extractPattern(content,
                    "v?\\d+\\.\\d+(?:\\.\\d+)?(?:-[a-zA-Z0-9]+)?", 5);

            // Detect rollback mentions
            boolean hasRollback = content.toLowerCase().contains("rollback");

            // Detect config changes
            boolean hasConfigChange = content.toLowerCase().contains("config") ||
                                      content.toLowerCase().contains("environment") ||
                                      content.toLowerCase().contains("env var");

            return String.format("""
                    {"deploymentContext": {
                      "hasRecentDeployment": %b,
                      "hasRollback": %b,
                      "hasConfigurationChange": %b,
                      "detectedVersions": %s,
                      "deploymentInfoLength": %d,
                      "deploymentSummary": "%s"
                    }}""",
                    hasDeployment, hasRollback, hasConfigChange,
                    toJsonArray(versions),
                    content.length(),
                    escapeJson(content.substring(0, Math.min(200, content.length()))));

        } catch (Exception e) {
            log.error("[Tool] getDeploymentContext failed: {}", e.getMessage());
            return "{\"error\": \"Deployment context extraction failed: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────

    private long countMatches(String text, String regex) {
        try {
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(text);
            long count = 0;
            while (m.find()) count++;
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    private List<String> extractPattern(String text, String regex, int maxMatches) {
        try {
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(text);
            List<String> results = new java.util.ArrayList<>();
            while (m.find() && results.size() < maxMatches) {
                String match = m.group().trim();
                if (!results.contains(match)) {
                    results.add(match);
                }
            }
            return results;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJsonArray(List<String> items) {
        return "[" + items.stream()
                .map(s -> "\"" + escapeJson(s) + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
