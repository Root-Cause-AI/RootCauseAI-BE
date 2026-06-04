package com.example.incidentinvestigator.controller;

import com.example.incidentinvestigator.dto.CreateIncidentRequest;
import com.example.incidentinvestigator.dto.IncidentResponse;
import com.example.incidentinvestigator.dto.IncidentSummaryResponse;
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
 * viewing incident details, triggering AI analysis, and retrieving
 * investigation reports.</p>
 *
 * <p>All endpoints are under {@code /api/incidents}.</p>
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);

    private final IncidentService incidentService;
    private final InvestigationService investigationService;

    public IncidentController(IncidentService incidentService,
                              InvestigationService investigationService) {
        this.incidentService = incidentService;
        this.investigationService = investigationService;
    }

    /**
     * Creates a new incident.
     *
     * @param request the incident data (title required, logs and deploymentInfo optional)
     * @return the created incident with HTTP 201
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
     *
     * @return list of incident summaries ordered by creation date descending
     */
    @GetMapping
    public ResponseEntity<List<IncidentSummaryResponse>> getAllIncidents(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        log.info("GET /api/incidents - Fetching incidents with filters");
        List<IncidentSummaryResponse> incidents = incidentService.getAllIncidents(severity, tags, search, startDate, endDate);
        return ResponseEntity.ok(incidents);
    }

    /**
     * Retrieves the full details of a single incident.
     *
     * @param id the incident UUID
     * @return the incident details
     */
    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> getIncident(@PathVariable UUID id) {
        log.info("GET /api/incidents/{} - Fetching incident", id);
        IncidentResponse response = incidentService.getIncident(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Triggers AI analysis for the specified incident.
     *
     * <p>This endpoint may take several seconds to respond as it
     * calls the LLM provider synchronously.</p>
     *
     * @param id the incident UUID
     * @return the generated investigation report
     */
    @PostMapping("/{id}/analyze")
    public ResponseEntity<ReportResponse> analyzeIncident(@PathVariable UUID id) {
        log.info("POST /api/incidents/{}/analyze - Starting analysis", id);
        ReportResponse report = investigationService.analyzeIncident(id);
        return ResponseEntity.ok(report);
    }

    /**
     * Retrieves the investigation report for a given incident.
     *
     * @param id the incident UUID
     * @return the investigation report
     */
    @GetMapping("/{id}/report")
    public ResponseEntity<ReportResponse> getReport(@PathVariable UUID id) {
        log.info("GET /api/incidents/{}/report - Fetching report", id);
        ReportResponse report = investigationService.getReport(id);
        return ResponseEntity.ok(report);
    }
}
