package com.example.incidentinvestigator.exception;

/**
 * Thrown when the AI/LLM provider encounters an error during analysis.
 *
 * <p>This covers API failures, timeouts, rate limits, and unexpected
 * responses from the LLM provider. Handled by the global exception
 * handler to return HTTP 502.</p>
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
