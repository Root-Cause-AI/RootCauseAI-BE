package com.example.incidentinvestigator.ai;

import com.example.incidentinvestigator.dto.AiAnalysisResponse;
import com.example.incidentinvestigator.exception.AnalysisException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Parses raw LLM output into a structured {@link AiAnalysisResponse}.
 *
 * <p>LLM output is inherently unreliable — it may include markdown
 * code fences, conversational preamble, or malformed JSON. This
 * component applies defensive parsing strategies:</p>
 *
 * <ol>
 *   <li>Strips markdown code fences (```json ... ```)</li>
 *   <li>Extracts the first JSON object from the text</li>
 *   <li>Validates required fields after deserialization</li>
 *   <li>Provides safe defaults for missing optional fields</li>
 * </ol>
 */
@Component
public class AiResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AiResponseParser.class);

    private final ObjectMapper objectMapper;

    public AiResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses the raw LLM response string into an {@link AiAnalysisResponse}.
     *
     * @param rawResponse the raw text from the LLM
     * @return the structured analysis response
     * @throws AnalysisException if parsing fails or required fields are missing
     */
    public AiAnalysisResponse parse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new AnalysisException("AI returned an empty response");
        }

        String cleaned = cleanResponse(rawResponse);

        try {
            AiAnalysisResponse response = objectMapper.readValue(cleaned, AiAnalysisResponse.class);
            validate(response);
            applyDefaults(response);
            return response;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI response as JSON. Raw: {}", rawResponse, e);
            throw new AnalysisException("Failed to parse AI response: " + e.getMessage(), e);
        }
    }

    /**
     * Cleans the raw response by removing markdown code fences and
     * extracting the JSON object.
     */
    private String cleanResponse(String raw) {
        String cleaned = raw.trim();

        // Remove markdown code fences: ```json ... ``` or ``` ... ```
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
        }

        // Extract JSON object if there's surrounding text
        int braceStart = cleaned.indexOf('{');
        int braceEnd = cleaned.lastIndexOf('}');
        if (braceStart != -1 && braceEnd != -1 && braceEnd > braceStart) {
            cleaned = cleaned.substring(braceStart, braceEnd + 1);
        }

        return cleaned;
    }

    /**
     * Validates that the response contains required fields.
     */
    private void validate(AiAnalysisResponse response) {
        if (response.getRootCause() == null || response.getRootCause().isBlank()) {
            throw new AnalysisException("AI response is missing the root cause");
        }
    }

    /**
     * Applies safe defaults for missing optional fields.
     */
    private void applyDefaults(AiAnalysisResponse response) {
        if (response.getConfidence() == null) {
            log.warn("AI response missing confidence score, defaulting to 0");
            response.setConfidence(0);
        }
        if (response.getEvidence() == null) {
            response.setEvidence(Collections.emptyList());
        }
        if (response.getRecommendations() == null) {
            response.setRecommendations(Collections.emptyList());
        }
    }
}
