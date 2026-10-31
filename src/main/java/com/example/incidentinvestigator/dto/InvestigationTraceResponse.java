package com.example.incidentinvestigator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestigationTraceResponse {
    private UUID id;
    private UUID jobId;
    private UUID incidentId;
    private String stage;
    private String agentName;
    private String toolName;
    private String toolArguments;
    private String summary;
    private String outputData;
    private String status;
    private Long durationMs;
    private Long llmLatencyMs;
    private Integer tokensUsed;
    private String errorMessage;
    private String evidenceReferences;
    private LocalDateTime createdAt;
}
