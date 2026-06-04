package com.example.incidentinvestigator.exception;

/**
 * Thrown when the AI analysis process encounters a non-provider error.
 *
 * <p>This covers issues such as invalid JSON parsing from the LLM
 * response, missing required fields, or empty analysis results.
 * Handled by the global exception handler to return HTTP 422.</p>
 */
public class AnalysisException extends RuntimeException {

    public AnalysisException(String message) {
        super(message);
    }

    public AnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
