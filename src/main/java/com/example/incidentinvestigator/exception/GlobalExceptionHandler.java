package com.example.incidentinvestigator.exception;

import com.example.incidentinvestigator.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Global exception handler for all REST controllers.
 *
 * <p>Catches known and unknown exceptions and translates them into
 * consistent {@link ErrorResponse} structures with appropriate HTTP
 * status codes. This ensures the API never leaks stack traces or
 * internal implementation details to the client.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles resource not found errors (HTTP 404).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles validation errors from @Valid annotations (HTTP 400).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Handles AI provider failures (HTTP 502).
     */
    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ErrorResponse> handleAiProvider(AiProviderException ex) {
        log.error("AI provider error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.BAD_GATEWAY, "AI analysis failed: " + ex.getMessage());
    }

    /**
     * Handles analysis processing errors (HTTP 422).
     */
    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<ErrorResponse> handleAnalysis(AnalysisException ex) {
        log.error("Analysis error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Analysis failed: " + ex.getMessage());
    }

    /**
     * Handles illegal argument exceptions (HTTP 400).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Catch-all handler for unexpected exceptions (HTTP 500).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    // ────────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        ErrorResponse error = ErrorResponse.builder()
                .status(status.value())
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(status).body(error);
    }
}
