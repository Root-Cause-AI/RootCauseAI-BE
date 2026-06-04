package com.example.incidentinvestigator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for full incident details.
 *
 * <p>Used when returning a single incident with all its fields,
 * including whether an investigation report has been generated.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentResponse {

    private UUID id;
    private String title;
    private String logs;
    private String deploymentInfo;
    private LocalDateTime createdAt;
    private boolean hasReport;
}
