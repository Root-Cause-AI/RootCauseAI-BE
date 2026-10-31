package com.example.incidentinvestigator.service;

import com.example.incidentinvestigator.ai.IncidentAnalysisService;
import com.example.incidentinvestigator.dto.AiAnalysisResponse;
import com.example.incidentinvestigator.dto.InvestigationJobResponse;
import com.example.incidentinvestigator.dto.ReportResponse;
import com.example.incidentinvestigator.entity.Incident;
import com.example.incidentinvestigator.entity.InvestigationJob;
import com.example.incidentinvestigator.entity.InvestigationReport;
import com.example.incidentinvestigator.exception.ResourceNotFoundException;
import com.example.incidentinvestigator.knowledge.RetrievalResult;
import com.example.incidentinvestigator.mapper.IncidentMapper;
import com.example.incidentinvestigator.repository.IncidentRepository;
import com.example.incidentinvestigator.repository.InvestigationJobRepository;
import com.example.incidentinvestigator.repository.InvestigationReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for AI-powered incident investigation.
 *
 * <p>Provides both synchronous (Phase 1 compatible) and asynchronous
 * (Phase A) investigation modes. The async mode submits a job, returns
 * immediately with a job ID, and executes the investigation in a
 * dedicated thread pool.</p>
 *
 * <p>Job lifecycle: PENDING → IN_PROGRESS → COMPLETED | FAILED</p>
 */
@Service
public class InvestigationService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationService.class);

    private final IncidentRepository incidentRepository;
    private final InvestigationReportRepository reportRepository;
    private final InvestigationJobRepository jobRepository;
    private final IncidentAnalysisService analysisService;
    private final com.example.incidentinvestigator.agent.orchestrator.InvestigationOrchestrator orchestrator;
    private final InvestigationContextBuilder contextBuilder;
    private final IncidentMapper mapper;

    public InvestigationService(IncidentRepository incidentRepository,
                                InvestigationReportRepository reportRepository,
                                InvestigationJobRepository jobRepository,
                                IncidentAnalysisService analysisService,
                                com.example.incidentinvestigator.agent.orchestrator.InvestigationOrchestrator orchestrator,
                                InvestigationContextBuilder contextBuilder,
                                IncidentMapper mapper) {
        this.incidentRepository = incidentRepository;
        this.reportRepository = reportRepository;
        this.jobRepository = jobRepository;
        this.analysisService = analysisService;
        this.orchestrator = orchestrator;
        this.contextBuilder = contextBuilder;
        this.mapper = mapper;
    }

    // ─────────────────────────────────────────────────────────────────
    // Async Investigation (Phase A)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Submits an asynchronous investigation job for the given incident.
     *
     * <p>Creates a PENDING job, launches the investigation in the background
     * thread pool, and returns the job immediately. Clients poll
     * {@code GET /api/incidents/{id}/job} to check status.</p>
     *
     * @param incidentId the UUID of the incident to analyze
     * @return the investigation job (status: PENDING)
     * @throws ResourceNotFoundException if the incident does not exist
     */
    @Transactional
    public InvestigationJobResponse submitAsyncInvestigation(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        // Don't create a duplicate job if one is already running
        if (jobRepository.existsByIncidentIdAndStatus(incidentId, InvestigationJob.Status.IN_PROGRESS)) {
            log.warn("Investigation already in progress for incident: {}", incidentId);
            InvestigationJob existing = jobRepository
                    .findTopByIncidentIdOrderByCreatedAtDesc(incidentId)
                    .orElseThrow();
            return toJobResponse(existing);
        }

        InvestigationJob job = InvestigationJob.builder()
                .incidentId(incidentId)
                .status(InvestigationJob.Status.PENDING)
                .stage("QUEUED")
                .build();
        job = jobRepository.save(job);
        log.info("Created investigation job {} for incident {}", job.getId(), incidentId);

        // Fire and forget — executes in investigationExecutor thread pool
        executeInvestigationAsync(job.getId(), incident);

        return toJobResponse(job);
    }

    /**
     * Retrieves the latest investigation job status for a given incident.
     *
     * @param incidentId the incident UUID
     * @return the latest job response
     */
    @Transactional(readOnly = true)
    public InvestigationJobResponse getJobStatus(UUID incidentId) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new ResourceNotFoundException("Incident", incidentId);
        }
        InvestigationJob job = jobRepository.findTopByIncidentIdOrderByCreatedAtDesc(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No investigation job found for incident: " + incidentId));
        return toJobResponse(job);
    }

    /**
     * Executes the investigation asynchronously in the dedicated thread pool.
     * Updates job status throughout execution.
     *
     * @param jobId    the ID of the job to update
     * @param incident the incident being investigated
     */
    @Async("investigationExecutor")
    public void executeInvestigationAsync(UUID jobId, Incident incident) {
        updateJobStatus(jobId, InvestigationJob.Status.IN_PROGRESS, "STARTING_INVESTIGATION", null, null);

        try {
            log.info("Starting async multi-agent investigation for job {} / incident {}", jobId, incident.getId());

            AiAnalysisResponse aiResponse = orchestrator.orchestrate(incident, jobId);

            updateJobStatus(jobId, InvestigationJob.Status.IN_PROGRESS, "PERSISTING_REPORT", null, null);
            List<RetrievalResult> ragContext = contextBuilder.buildContext(incident);
            persistReport(incident, aiResponse, ragContext);

            updateJobStatus(jobId, InvestigationJob.Status.COMPLETED, "DONE", LocalDateTime.now(), null);
            log.info("Async investigation completed for job {} / incident {}", jobId, incident.getId());

        } catch (Exception e) {
            log.error("Async investigation failed for job {} / incident {}: {}",
                    jobId, incident.getId(), e.getMessage(), e);
            updateJobStatus(jobId, InvestigationJob.Status.FAILED, "FAILED",
                    LocalDateTime.now(), e.getMessage());
        }
    }

    @Transactional
    public void updateJobStatus(UUID jobId, InvestigationJob.Status status,
                                 String stage, LocalDateTime completedAt, String errorMessage) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setStage(stage);
            if (status == InvestigationJob.Status.IN_PROGRESS && job.getStartedAt() == null) {
                job.setStartedAt(LocalDateTime.now());
            }
            if (completedAt != null) {
                job.setCompletedAt(completedAt);
            }
            if (errorMessage != null) {
                job.setErrorMessage(errorMessage);
            }
            jobRepository.save(job);
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // Synchronous Investigation (backward-compatible Phase 1 API)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Triggers synchronous AI analysis for the specified incident.
     *
     * <p>Preserved for backward API compatibility. New callers should
     * use {@link #submitAsyncInvestigation(UUID)} instead.</p>
     *
     * @param incidentId the UUID of the incident to analyze
     * @return the generated investigation report
     * @throws ResourceNotFoundException if the incident does not exist
     */
    @Transactional
    public ReportResponse analyzeIncident(UUID incidentId) {
        log.info("Starting synchronous analysis for incident: {}", incidentId);

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        // Run agentic multi-stage investigation
        AiAnalysisResponse aiResponse = orchestrator.orchestrate(incident, null);

        // Retrieve relevant knowledge base context for report metadata
        List<RetrievalResult> ragContext = contextBuilder.buildContext(incident);

        // Persist the report
        InvestigationReport saved = persistReport(incident, aiResponse, ragContext);

        log.info("Synchronous analysis complete for incident: {}. Confidence: {}%, RAG sources: {}",
                incidentId, saved.getConfidenceScore(), ragContext.size());

        return mapper.toReportResponse(saved);
    }

    /**
     * Retrieves the investigation report for a given incident.
     *
     * @param incidentId the UUID of the incident
     * @return the report response
     * @throws ResourceNotFoundException if no report exists for the incident
     */
    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID incidentId) {
        log.debug("Fetching report for incident: {}", incidentId);

        if (!incidentRepository.existsById(incidentId)) {
            throw new ResourceNotFoundException("Incident", incidentId);
        }

        InvestigationReport report = reportRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Report not found for incident: " + incidentId));

        return mapper.toReportResponse(report);
    }

    // ─────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────

    @Transactional
    protected InvestigationReport persistReport(Incident incident, AiAnalysisResponse aiResponse,
                                                List<RetrievalResult> ragContext) {
        // Upsert report in-place to avoid deleting attached entities which causes
        // Hibernate TransientObjectException with bidirectional @OneToOne relations.
        InvestigationReport existingReport = reportRepository.findByIncidentId(incident.getId())
                .orElse(incident.getReport());

        InvestigationReport report;
        if (existingReport != null) {
            report = mapper.updateReportEntity(existingReport, aiResponse, ragContext);
        } else {
            report = mapper.toReportEntity(incident, aiResponse, ragContext);
        }

        InvestigationReport saved = reportRepository.save(report);
        incident.setReport(saved);
        return saved;
    }

    private InvestigationJobResponse toJobResponse(InvestigationJob job) {
        return InvestigationJobResponse.builder()
                .jobId(job.getId())
                .incidentId(job.getIncidentId())
                .status(job.getStatus().name())
                .stage(job.getStage())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .build();
    }
}
