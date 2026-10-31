package com.example.incidentinvestigator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity tracking persistent observability traces for agentic multi-stage
 * investigations, tool invocations, performance metrics, and evidence citations.
 */
@Entity
@Table(name = "investigation_traces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestigationTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(nullable = false, length = 50)
    private String stage;

    @Column(name = "agent_name", length = 50)
    private String agentName;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "tool_arguments", columnDefinition = "TEXT")
    private String toolArguments;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "output_data", columnDefinition = "TEXT")
    private String outputData;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "SUCCESS";

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "llm_latency_ms")
    private Long llmLatencyMs;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "evidence_references", columnDefinition = "TEXT")
    private String evidenceReferences;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
