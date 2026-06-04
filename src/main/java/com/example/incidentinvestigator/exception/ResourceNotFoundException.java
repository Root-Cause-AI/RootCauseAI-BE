package com.example.incidentinvestigator.exception;

/**
 * Thrown when a requested resource (incident, report) cannot be found.
 *
 * <p>Handled by the global exception handler to return HTTP 404.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Object id) {
        super(String.format("%s not found with id: %s", resourceName, id));
    }
}
