package com.example.incidentinvestigator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Standard error response DTO.
 *
 * <p>Returned by the global exception handler to provide
 * consistent error structures across all API endpoints.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    private int status;
    private String message;
    private LocalDateTime timestamp;
}
