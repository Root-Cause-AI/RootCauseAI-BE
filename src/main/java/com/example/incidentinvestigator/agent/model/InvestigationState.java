package com.example.incidentinvestigator.agent.model;

import com.example.incidentinvestigator.entity.Incident;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestigationState {
    private Incident incident;
    private UUID jobId;
    private LogAnalysisResult logAnalysis;
    private RetrievedContextResult retrievedContext;
    private HypothesisResult hypothesisResult;
    private RemediationResult remediationResult;

    @Builder.Default
    private int iterationCount = 0;
    @Builder.Default
    private int toolCallCount = 0;
    @Builder.Default
    private int maxIterations = 2;
    @Builder.Default
    private int maxToolCalls = 8;
    @Builder.Default
    private long startTimeMs = System.currentTimeMillis();

    public boolean canIterate() {
        return iterationCount < maxIterations && toolCallCount < maxToolCalls;
    }

    public void incrementIteration() {
        this.iterationCount++;
    }

    public void incrementToolCall() {
        this.toolCallCount++;
    }
}
