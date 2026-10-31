package com.example.incidentinvestigator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO representing the status of an asynchronous investigation job.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestigationJobResponse {

    private UUID jobId;
    private UUID incidentId;
    private String status;
    private String stage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
}
