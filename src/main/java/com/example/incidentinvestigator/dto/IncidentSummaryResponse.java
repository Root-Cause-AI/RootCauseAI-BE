package com.example.incidentinvestigator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Summary response DTO for incident list views.
 *
 * <p>A lightweight representation that excludes bulky log/deployment
 * data, used when listing all incidents for the dashboard.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentSummaryResponse {

    private UUID id;
    private String title;
    private LocalDateTime createdAt;
    private boolean hasReport;
    private String severity;
    private List<String> tags;
}
