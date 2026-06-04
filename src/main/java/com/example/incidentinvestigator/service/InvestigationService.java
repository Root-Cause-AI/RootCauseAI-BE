package com.example.incidentinvestigator.service;

import com.example.incidentinvestigator.ai.IncidentAnalysisService;
import com.example.incidentinvestigator.dto.AiAnalysisResponse;
import com.example.incidentinvestigator.dto.ReportResponse;
import com.example.incidentinvestigator.entity.Incident;
import com.example.incidentinvestigator.entity.InvestigationReport;
import com.example.incidentinvestigator.exception.ResourceNotFoundException;
import com.example.incidentinvestigator.knowledge.RetrievalResult;
import com.example.incidentinvestigator.mapper.IncidentMapper;
import com.example.incidentinvestigator.repository.IncidentRepository;
import com.example.incidentinvestigator.repository.InvestigationReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service layer for AI-powered incident investigation.
 *
 * <p>Orchestrates the full analysis workflow: fetching the incident,
 * retrieving relevant knowledge base context (RAG), invoking the AI
 * analysis service, persisting the report, and returning the structured
 * result.</p>
 */
@Service
public class InvestigationService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationService.class);

    private final IncidentRepository incidentRepository;
    private final InvestigationReportRepository reportRepository;
    private final IncidentAnalysisService analysisService;
    private final InvestigationContextBuilder contextBuilder;
    private final IncidentMapper mapper;

    public InvestigationService(IncidentRepository incidentRepository,
                                InvestigationReportRepository reportRepository,
                                IncidentAnalysisService analysisService,
                                InvestigationContextBuilder contextBuilder,
                                IncidentMapper mapper) {
        this.incidentRepository = incidentRepository;
        this.reportRepository = reportRepository;
        this.analysisService = analysisService;
        this.contextBuilder = contextBuilder;
        this.mapper = mapper;
    }

    /**
     * Triggers AI analysis for the specified incident.
     *
     * <p>If a report already exists for this incident, it is deleted and
     * regenerated. This allows users to re-run analysis if needed.</p>
     *
     * <p>Phase 3: Before calling the LLM, the service retrieves relevant
     * knowledge base documents via hybrid search and injects them as
     * RAG context into the analysis prompt.</p>
     *
     * @param incidentId the UUID of the incident to analyze
     * @return the generated investigation report
     * @throws ResourceNotFoundException if the incident does not exist
     */
    @Transactional
    public ReportResponse analyzeIncident(UUID incidentId) {
        log.info("Starting analysis for incident: {}", incidentId);

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        // Delete existing report if re-analyzing
        reportRepository.findByIncidentId(incidentId)
                .ifPresent(existing -> {
                    log.info("Deleting existing report for incident: {}", incidentId);
                    reportRepository.delete(existing);
                    reportRepository.flush();
                });

        // Fetch historical context (up to 5 recent reports)
        List<InvestigationReport> pastReports = reportRepository.findTop5ByOrderByCreatedAtDesc();

        // Phase 3: Retrieve relevant knowledge base context
        List<RetrievalResult> ragContext = contextBuilder.buildContext(incident);

        // Run AI analysis with RAG context
        AiAnalysisResponse aiResponse = analysisService.analyze(
                incident.getLogs(),
                incident.getDeploymentInfo(),
                pastReports,
                ragContext
        );

        // Persist the report with RAG context
        InvestigationReport report = mapper.toReportEntity(incident, aiResponse, ragContext);
        InvestigationReport saved = reportRepository.save(report);

        log.info("Analysis complete for incident: {}. Confidence: {}%, RAG sources: {}",
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

        // Verify the incident exists first
        if (!incidentRepository.existsById(incidentId)) {
            throw new ResourceNotFoundException("Incident", incidentId);
        }

        InvestigationReport report = reportRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Report not found for incident: " + incidentId));

        return mapper.toReportResponse(report);
    }
}
