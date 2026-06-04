package com.example.incidentinvestigator.ai;

import com.example.incidentinvestigator.entity.InvestigationReport;
import com.example.incidentinvestigator.knowledge.RetrievalResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds structured prompts for the LLM provider.
 *
 * <p>This component centralizes all prompt engineering. The prompt
 * instructs the model to act as a senior SRE and return a strictly
 * formatted JSON response with root cause analysis, confidence
 * score, evidence, and recommendations.</p>
 *
 * <p>Phase 3 addition: RAG context from the knowledge base is injected
 * into the prompt so the LLM can reference runbooks, past incidents,
 * and known fixes when analyzing new incidents.</p>
 */
@Component
public class PromptBuilder {

    private static final String PROMPT_TEMPLATE = """
            You are a senior Site Reliability Engineer with extensive experience \
            in diagnosing production incidents.

            Analyze the provided application logs and deployment information carefully.

            Determine:
            1. The most likely root cause of the incident
            2. A confidence score from 0 to 100 indicating how certain you are
            3. A severity classification (LOW, MEDIUM, HIGH, CRITICAL)
            4. Relevant tags for filtering (e.g., DATABASE, MEMORY, DEPLOYMENT, NETWORK)
            5. An executive summary (business-focused)
            6. An engineering summary (technical details)
            7. A chronological timeline of events extracted from logs and deployment data
            8. Supporting evidence from the logs and deployment data, with severity
            9. Recommended actions to resolve and prevent recurrence, prioritized
            10. Comparison to previous historical incidents provided below
            11. Citations referencing any knowledge base documents used in your analysis

            IMPORTANT RULES:
            - Avoid hallucinations at all costs.
            - Reference evidence from the logs explicitly.
            - Cite supporting documents accurately in the citations array.

            Return ONLY valid JSON matching this exact schema. Do not include any \
            text before or after the JSON object.

            {
              "rootCause": "A clear, concise description of the root cause",
              "confidence": 85,
              "severity": "HIGH",
              "tags": ["DATABASE", "NETWORK"],
              "executiveSummary": "A high-level summary of the incident impact",
              "engineeringSummary": "A technical summary of the failure",
              "timeline": [
                {
                  "timestamp": "2023-10-01T12:00:00Z",
                  "event": "Deployment started",
                  "type": "DEPLOYMENT"
                }
              ],
              "evidence": [
                {
                  "text": "Connection timeout to DB",
                  "timestamp": "2023-10-01T12:05:00Z",
                  "severity": "ERROR"
                }
              ],
              "recommendations": [
                {
                  "priority": "HIGH",
                  "action": "Increase connection pool size"
                }
              ],
              "similarIncidents": [
                {
                  "incidentId": "uuid-here",
                  "reason": "Similar database connection timeout pattern",
                  "similarity": "HIGH"
                }
              ],
              "citations": [
                {
                  "documentTitle": "Name of the knowledge base document",
                  "documentType": "RUNBOOK",
                  "excerpt": "Relevant excerpt from the document",
                  "relevance": "How this document relates to the incident"
                }
              ]
            }

            --- KNOWLEDGE BASE CONTEXT ---
            %s

            --- PAST INCIDENTS FOR COMPARISON ---
            %s

            --- APPLICATION LOGS ---
            %s

            --- DEPLOYMENT INFORMATION ---
            %s
            """;

    /**
     * Builds the analysis prompt with RAG context, historical data, and incident data.
     *
     * @param logs           the application logs (may be null or empty)
     * @param deploymentInfo the deployment information (may be null or empty)
     * @param pastReports    recent reports for historical context
     * @param ragContext     retrieved knowledge base chunks for RAG augmentation
     * @return the fully constructed prompt string
     */
    public String buildPrompt(String logs, String deploymentInfo,
                              List<InvestigationReport> pastReports,
                              List<RetrievalResult> ragContext) {
        String sanitizedLogs = sanitize(logs, "No logs provided.");
        String sanitizedDeployment = sanitize(deploymentInfo, "No deployment information provided.");

        String ragContextStr = buildRagContextSection(ragContext);
        String pastIncidentsStr = buildPastIncidentsSection(pastReports);

        return String.format(PROMPT_TEMPLATE, ragContextStr, pastIncidentsStr,
                sanitizedLogs, sanitizedDeployment);
    }

    /**
     * Backward-compatible overload without RAG context.
     */
    public String buildPrompt(String logs, String deploymentInfo,
                              List<InvestigationReport> pastReports) {
        return buildPrompt(logs, deploymentInfo, pastReports, List.of());
    }

    private String buildRagContextSection(List<RetrievalResult> ragContext) {
        if (ragContext == null || ragContext.isEmpty()) {
            return "No knowledge base documents available. Analyze based on logs and deployment data only.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("The following documents from the knowledge base are relevant to this incident.\n");
        sb.append("Use them to inform your analysis. CITE the document title when referencing them.\n\n");

        for (RetrievalResult result : ragContext) {
            sb.append(String.format("[Document: \"%s\" (%s) | Relevance: %.4f]\n",
                    result.documentTitle(), result.documentType(), result.relevanceScore()));
            sb.append(result.content());
            sb.append("\n\n---\n\n");
        }

        return sb.toString();
    }

    private String buildPastIncidentsSection(List<InvestigationReport> pastReports) {
        if (pastReports == null || pastReports.isEmpty()) {
            return "No past incidents available.";
        }

        return pastReports.stream()
                .map(r -> "Incident ID: " + r.getIncident().getId() + "\\n" +
                          "Title: " + r.getIncident().getTitle() + "\\n" +
                          "Root Cause: " + r.getRootCause() + "\\n" +
                          "Tags: " + r.getTags() + "\\n")
                .collect(Collectors.joining("\\n---\\n"));
    }

    /**
     * Sanitizes input data, replacing null or blank values with a default.
     */
    private String sanitize(String input, String defaultValue) {
        if (input == null || input.isBlank()) {
            return defaultValue;
        }
        return input.trim();
    }
}
