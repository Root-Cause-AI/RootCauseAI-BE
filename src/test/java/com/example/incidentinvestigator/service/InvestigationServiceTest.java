package com.example.incidentinvestigator.service;

import com.example.incidentinvestigator.ai.IncidentAnalysisService;
import com.example.incidentinvestigator.dto.AiAnalysisResponse;
import com.example.incidentinvestigator.dto.ReportResponse;
import com.example.incidentinvestigator.dto.EvidenceItem;
import com.example.incidentinvestigator.dto.Recommendation;
import com.example.incidentinvestigator.entity.Incident;
import com.example.incidentinvestigator.entity.InvestigationReport;
import com.example.incidentinvestigator.exception.ResourceNotFoundException;
import com.example.incidentinvestigator.mapper.IncidentMapper;
import com.example.incidentinvestigator.repository.IncidentRepository;
import com.example.incidentinvestigator.repository.InvestigationReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvestigationService Tests")
class InvestigationServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private InvestigationReportRepository reportRepository;

    @Mock
    private IncidentAnalysisService analysisService;

    @Mock
    private InvestigationContextBuilder contextBuilder;

    @Mock
    private IncidentMapper mapper;

    @InjectMocks
    private InvestigationService investigationService;

    private UUID incidentId;
    private Incident testIncident;
    private AiAnalysisResponse aiResponse;
    private InvestigationReport testReport;
    private ReportResponse testReportResponse;

    @BeforeEach
    void setUp() {
        incidentId = UUID.randomUUID();
        testIncident = Incident.builder()
                .id(incidentId)
                .title("Payment Service Failure")
                .logs("ERROR: Connection timeout")
                .deploymentInfo("version: 2.1.0")
                .createdAt(LocalDateTime.now())
                .build();

        aiResponse = AiAnalysisResponse.builder()
                .rootCause("Database connection pool exhausted")
                .confidence(85)
                .evidence(List.of(EvidenceItem.builder().text("Connection timeout errors in logs").build()))
                .recommendations(List.of(Recommendation.builder().action("Increase connection pool size").build()))
                .build();

        testReport = InvestigationReport.builder()
                .id(UUID.randomUUID())
                .incident(testIncident)
                .rootCause("Database connection pool exhausted")
                .confidenceScore(85)
                .evidence("[\"Connection timeout errors in logs\"]")
                .recommendations("[\"Increase connection pool size\"]")
                .createdAt(LocalDateTime.now())
                .build();

        testReportResponse = ReportResponse.builder()
                .id(testReport.getId())
                .incidentId(incidentId)
                .rootCause("Database connection pool exhausted")
                .confidenceScore(85)
                .evidence(List.of(EvidenceItem.builder().text("Connection timeout errors in logs").build()))
                .recommendations(List.of(Recommendation.builder().action("Increase connection pool size").build()))
                .createdAt(testReport.getCreatedAt())
                .build();
    }

    @Nested
    @DisplayName("analyzeIncident")
    class AnalyzeIncident {

        @Test
        @DisplayName("should analyze incident and return report")
        void shouldAnalyzeAndReturnReport() {
            when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));
            when(reportRepository.findByIncidentId(incidentId)).thenReturn(Optional.empty());
            when(contextBuilder.buildContext(any())).thenReturn(List.of());
            when(analysisService.analyze(any(), any(), any(), any()))
                    .thenReturn(aiResponse);
            when(mapper.toReportEntity(any(), any(), any())).thenReturn(testReport);
            when(reportRepository.save(testReport)).thenReturn(testReport);
            when(mapper.toReportResponse(testReport)).thenReturn(testReportResponse);

            ReportResponse result = investigationService.analyzeIncident(incidentId);

            assertThat(result).isNotNull();
            assertThat(result.getRootCause()).isEqualTo("Database connection pool exhausted");
            assertThat(result.getConfidenceScore()).isEqualTo(85);
            verify(reportRepository).save(any(InvestigationReport.class));
        }

        @Test
        @DisplayName("should delete existing report before re-analysis")
        void shouldDeleteExistingReport() {
            InvestigationReport existingReport = InvestigationReport.builder()
                    .id(UUID.randomUUID())
                    .build();

            when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));
            when(reportRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(existingReport));
            when(contextBuilder.buildContext(any())).thenReturn(List.of());
            when(analysisService.analyze(any(), any(), any(), any())).thenReturn(aiResponse);
            when(mapper.toReportEntity(any(), any(), any())).thenReturn(testReport);
            when(reportRepository.save(testReport)).thenReturn(testReport);
            when(mapper.toReportResponse(testReport)).thenReturn(testReportResponse);

            investigationService.analyzeIncident(incidentId);

            verify(reportRepository).delete(existingReport);
            verify(reportRepository).flush();
        }

        @Test
        @DisplayName("should throw when incident not found")
        void shouldThrowWhenIncidentNotFound() {
            UUID missingId = UUID.randomUUID();
            when(incidentRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> investigationService.analyzeIncident(missingId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(analysisService, never()).analyze(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getReport")
    class GetReport {

        @Test
        @DisplayName("should return report when found")
        void shouldReturnReportWhenFound() {
            when(incidentRepository.existsById(incidentId)).thenReturn(true);
            when(reportRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(testReport));
            when(mapper.toReportResponse(testReport)).thenReturn(testReportResponse);

            ReportResponse result = investigationService.getReport(incidentId);

            assertThat(result).isNotNull();
            assertThat(result.getRootCause()).isEqualTo("Database connection pool exhausted");
        }

        @Test
        @DisplayName("should throw when incident not found")
        void shouldThrowWhenIncidentNotFound() {
            UUID missingId = UUID.randomUUID();
            when(incidentRepository.existsById(missingId)).thenReturn(false);

            assertThatThrownBy(() -> investigationService.getReport(missingId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when report not found for existing incident")
        void shouldThrowWhenReportNotFound() {
            when(incidentRepository.existsById(incidentId)).thenReturn(true);
            when(reportRepository.findByIncidentId(incidentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> investigationService.getReport(incidentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
