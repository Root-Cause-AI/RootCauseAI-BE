package com.example.incidentinvestigator.controller;

import com.example.incidentinvestigator.dto.CreateIncidentRequest;
import com.example.incidentinvestigator.dto.IncidentResponse;
import com.example.incidentinvestigator.dto.IncidentSummaryResponse;
import com.example.incidentinvestigator.dto.InvestigationJobResponse;
import com.example.incidentinvestigator.dto.ReportResponse;
import com.example.incidentinvestigator.service.IncidentService;
import com.example.incidentinvestigator.service.InvestigationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for incident management and investigation.
 *
 * <p>Provides endpoints for creating incidents, listing incidents,
 * viewing incident details, triggering AI analysis (sync and async),
 * and retrieving investigation reports and job status.</p>
 *
 * <p>All endpoints are under {@code /api/incidents}.</p>
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);

    private final IncidentService incidentService;
    private final InvestigationService investigationService;
    private final com.example.incidentinvestigator.service.InvestigationTraceService traceService;

    public IncidentController(IncidentService incidentService,
                              InvestigationService investigationService,
                              com.example.incidentinvestigator.service.InvestigationTraceService traceService) {
        this.incidentService = incidentService;
        this.investigationService = investigationService;
        this.traceService = traceService;
    }

    /**
     * Creates a new incident.
     */
    @PostMapping
    public ResponseEntity<IncidentResponse> createIncident(
            @Valid @RequestBody CreateIncidentRequest request) {
        log.info("POST /api/incidents - Creating incident: {}", request.getTitle());
        IncidentResponse response = incidentService.createIncident(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all incidents as summary objects.
     */
    @GetMapping
    public ResponseEntity<List<IncidentSummaryResponse>> getAllIncidents(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        log.info("GET /api/incidents - Fetching incidents with filters");
        List<IncidentSummaryResponse> incidents = incidentService.getAllIncidents(
                severity, tags, search, startDate, endDate);
        return ResponseEntity.ok(incidents);
    }

    /**
     * Retrieves the full details of a single incident.
     */
    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> getIncident(@PathVariable UUID id) {
        log.info("GET /api/incidents/{} - Fetching incident", id);
        IncidentResponse response = incidentService.getIncident(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Triggers synchronous AI analysis for the specified incident.
     *
     * <p>Backward-compatible endpoint — blocks until the LLM finishes.
     * For long-running investigations use the async endpoint instead.</p>
     */
    @PostMapping("/{id}/analyze")
    public ResponseEntity<ReportResponse> analyzeIncident(@PathVariable UUID id) {
        log.info("POST /api/incidents/{}/analyze - Starting synchronous analysis", id);
        ReportResponse report = investigationService.analyzeIncident(id);
        return ResponseEntity.ok(report);
    }

    /**
     * Submits an asynchronous investigation job for the specified incident.
     *
     * <p>Returns immediately with a job ID and PENDING status. Poll
     * {@code GET /api/incidents/{id}/job} to check progress.</p>
     */
    @PostMapping("/{id}/analyze/async")
    public ResponseEntity<InvestigationJobResponse> analyzeIncidentAsync(@PathVariable UUID id) {
        log.info("POST /api/incidents/{}/analyze/async - Submitting async investigation", id);
        InvestigationJobResponse job = investigationService.submitAsyncInvestigation(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    /**
     * Gets the latest investigation job status for a given incident.
     */
    @GetMapping("/{id}/job")
    public ResponseEntity<InvestigationJobResponse> getJobStatus(@PathVariable UUID id) {
        log.info("GET /api/incidents/{}/job - Fetching job status", id);
        InvestigationJobResponse job = investigationService.getJobStatus(id);
        return ResponseEntity.ok(job);
    }

    /**
     * Retrieves the investigation report for a given incident.
     */
    @GetMapping("/{id}/report")
    public ResponseEntity<ReportResponse> getReport(@PathVariable UUID id) {
        log.info("GET /api/incidents/{}/report - Fetching report", id);
        ReportResponse report = investigationService.getReport(id);
        return ResponseEntity.ok(report);
    }

    /**
     * Retrieves the persistent execution traces for an incident.
     */
    @GetMapping("/{id}/traces")
    public ResponseEntity<List<com.example.incidentinvestigator.dto.InvestigationTraceResponse>> getIncidentTraces(@PathVariable UUID id) {
        log.info("GET /api/incidents/{}/traces - Fetching traces", id);
        return ResponseEntity.ok(traceService.getTracesByIncident(id));
    }

    /**
     * Retrieves the persistent execution traces for a specific investigation job.
     */
    @GetMapping("/jobs/{jobId}/traces")
    public ResponseEntity<List<com.example.incidentinvestigator.dto.InvestigationTraceResponse>> getJobTraces(@PathVariable UUID jobId) {
        log.info("GET /api/incidents/jobs/{}/traces - Fetching job traces", jobId);
        return ResponseEntity.ok(traceService.getTracesByJob(jobId));
    }
}
