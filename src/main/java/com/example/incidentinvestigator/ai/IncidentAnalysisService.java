package com.example.incidentinvestigator.ai;

import com.example.incidentinvestigator.dto.AiAnalysisResponse;
import com.example.incidentinvestigator.entity.InvestigationReport;
import com.example.incidentinvestigator.knowledge.RetrievalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the AI incident analysis workflow.
 *
 * <p>This service ties together the three AI layer components:</p>
 * <ol>
 *   <li>{@link PromptBuilder} — constructs the analysis prompt</li>
 *   <li>{@link LlmClient} — sends the prompt to the LLM provider</li>
 *   <li>{@link AiResponseParser} — parses and validates the response</li>
 * </ol>
 *
 * <p>The service is deliberately thin — it coordinates but does not
 * contain business logic. Persistence is handled by
 * {@link com.example.incidentinvestigator.service.InvestigationService}.</p>
 */
@Service
public class IncidentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(IncidentAnalysisService.class);

    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final AiResponseParser responseParser;

    public IncidentAnalysisService(PromptBuilder promptBuilder,
                                   LlmClient llmClient,
                                   AiResponseParser responseParser) {
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.responseParser = responseParser;
    }

    /**
     * Performs AI analysis with RAG context from the knowledge base.
     *
     * @param logs           the application logs
     * @param deploymentInfo the deployment information
     * @param pastReports    recent reports for historical context
     * @param ragContext     retrieved knowledge base chunks
     * @return the structured analysis response
     */
    public AiAnalysisResponse analyze(String logs, String deploymentInfo,
                                      List<InvestigationReport> pastReports,
                                      List<RetrievalResult> ragContext) {
        log.info("Starting AI analysis with {} RAG context chunks", 
                ragContext != null ? ragContext.size() : 0);

        // Step 1: Build prompt with RAG context
        String prompt = promptBuilder.buildPrompt(logs, deploymentInfo, pastReports, ragContext);
        log.debug("Prompt built (length: {} chars)", prompt.length());

        // Step 2: Call LLM
        String rawResponse = llmClient.analyze(prompt);
        log.debug("Raw LLM response received");

        // Step 3: Parse response
        AiAnalysisResponse analysisResponse = responseParser.parse(rawResponse);
        log.info("AI analysis complete. Root cause: {}, Confidence: {}%",
                analysisResponse.getRootCause(),
                analysisResponse.getConfidence());

        return analysisResponse;
    }

    /**
     * Backward-compatible overload without RAG context.
     */
    public AiAnalysisResponse analyze(String logs, String deploymentInfo,
                                      List<InvestigationReport> pastReports) {
        return analyze(logs, deploymentInfo, pastReports, List.of());
    }
}
