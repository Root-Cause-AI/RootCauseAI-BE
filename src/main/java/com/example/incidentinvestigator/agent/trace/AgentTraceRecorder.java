package com.example.incidentinvestigator.agent.trace;

import com.example.incidentinvestigator.entity.InvestigationTrace;
import com.example.incidentinvestigator.repository.InvestigationTraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for recording execution traces and observability metrics
 * during agentic incident investigations.
 */
@Service
public class AgentTraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceRecorder.class);

    private final InvestigationTraceRepository traceRepository;

    public AgentTraceRecorder(InvestigationTraceRepository traceRepository) {
        this.traceRepository = traceRepository;
    }

    @Transactional
    public InvestigationTrace recordStageTrace(UUID incidentId, UUID jobId, String stage,
                                               String agentName, String summary,
                                               String outputData, long durationMs,
                                               long llmLatencyMs, Integer tokensUsed) {
        log.info("[Trace] Stage: {} by Agent: {} (duration: {}ms, llm: {}ms)",
                stage, agentName, durationMs, llmLatencyMs);

        InvestigationTrace trace = InvestigationTrace.builder()
                .incidentId(incidentId)
                .jobId(jobId)
                .stage(stage)
                .agentName(agentName)
                .summary(truncate(summary, 2000))
                .outputData(truncate(outputData, 5000))
                .status("SUCCESS")
                .durationMs(durationMs)
                .llmLatencyMs(llmLatencyMs)
                .tokensUsed(tokensUsed)
                .createdAt(LocalDateTime.now())
                .build();

        return traceRepository.save(trace);
    }

    @Transactional
    public InvestigationTrace recordToolTrace(UUID incidentId, UUID jobId, String stage,
                                              String agentName, String toolName,
                                              String toolArgs, String summary,
                                              String outputData, String evidenceRefs,
                                              long durationMs, boolean success) {
        log.info("[Trace] Tool: {} invoked by {} - Status: {} (duration: {}ms)",
                toolName, agentName, success ? "SUCCESS" : "FAILED", durationMs);

        InvestigationTrace trace = InvestigationTrace.builder()
                .incidentId(incidentId)
                .jobId(jobId)
                .stage(stage)
                .agentName(agentName)
                .toolName(toolName)
                .toolArguments(truncate(toolArgs, 1000))
                .summary(truncate(summary, 1000))
                .outputData(truncate(outputData, 4000))
                .evidenceReferences(truncate(evidenceRefs, 1000))
                .status(success ? "SUCCESS" : "FAILED")
                .durationMs(durationMs)
                .createdAt(LocalDateTime.now())
                .build();

        return traceRepository.save(trace);
    }

    @Transactional
    public InvestigationTrace recordErrorTrace(UUID incidentId, UUID jobId, String stage,
                                               String agentName, String errorMessage,
                                               long durationMs) {
        log.error("[Trace] Error in stage: {} by Agent: {} - {}", stage, agentName, errorMessage);

        InvestigationTrace trace = InvestigationTrace.builder()
                .incidentId(incidentId)
                .jobId(jobId)
                .stage(stage)
                .agentName(agentName)
                .status("FAILED")
                .errorMessage(truncate(errorMessage, 2000))
                .durationMs(durationMs)
                .createdAt(LocalDateTime.now())
                .build();

        return traceRepository.save(trace);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
