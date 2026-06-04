package com.example.incidentinvestigator.controller;

import com.example.incidentinvestigator.dto.CreateIncidentRequest;
import com.example.incidentinvestigator.dto.IncidentResponse;
import com.example.incidentinvestigator.dto.IncidentSummaryResponse;
import com.example.incidentinvestigator.dto.ReportResponse;
import com.example.incidentinvestigator.dto.EvidenceItem;
import com.example.incidentinvestigator.dto.Recommendation;
import com.example.incidentinvestigator.exception.GlobalExceptionHandler;
import com.example.incidentinvestigator.exception.ResourceNotFoundException;
import com.example.incidentinvestigator.service.IncidentService;
import com.example.incidentinvestigator.service.InvestigationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("IncidentController Tests")
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IncidentService incidentService;

    @MockitoBean
    private InvestigationService investigationService;

    @Test
    @DisplayName("POST /api/incidents - should create incident and return 201")
    void shouldCreateIncident() throws Exception {
        UUID id = UUID.randomUUID();
        CreateIncidentRequest request = CreateIncidentRequest.builder()
                .title("Payment Service Failure")
                .logs("ERROR: timeout")
                .deploymentInfo("v2.1.0")
                .build();

        IncidentResponse response = IncidentResponse.builder()
                .id(id)
                .title("Payment Service Failure")
                .logs("ERROR: timeout")
                .deploymentInfo("v2.1.0")
                .createdAt(LocalDateTime.now())
                .hasReport(false)
                .build();

        when(incidentService.createIncident(any(CreateIncidentRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Payment Service Failure"));
    }

    @Test
    @DisplayName("POST /api/incidents - should return 400 for blank title")
    void shouldRejectBlankTitle() throws Exception {
        CreateIncidentRequest request = CreateIncidentRequest.builder()
                .title("")
                .build();

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /api/incidents - should return all incidents")
    void shouldReturnAllIncidents() throws Exception {
        IncidentSummaryResponse summary = IncidentSummaryResponse.builder()
                .id(UUID.randomUUID())
                .title("Payment Service Failure")
                .createdAt(LocalDateTime.now())
                .hasReport(true)
                .build();

        when(incidentService.getAllIncidents(any(), any(), any(), any(), any())).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Payment Service Failure"))
                .andExpect(jsonPath("$[0].hasReport").value(true));
    }

    @Test
    @DisplayName("GET /api/incidents/{id} - should return incident")
    void shouldReturnIncident() throws Exception {
        UUID id = UUID.randomUUID();
        IncidentResponse response = IncidentResponse.builder()
                .id(id)
                .title("Payment Service Failure")
                .createdAt(LocalDateTime.now())
                .hasReport(false)
                .build();

        when(incidentService.getIncident(id)).thenReturn(response);

        mockMvc.perform(get("/api/incidents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Payment Service Failure"));
    }

    @Test
    @DisplayName("GET /api/incidents/{id} - should return 404 when not found")
    void shouldReturn404WhenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(incidentService.getIncident(id))
                .thenThrow(new ResourceNotFoundException("Incident", id));

        mockMvc.perform(get("/api/incidents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/incidents/{id}/analyze - should return report")
    void shouldAnalyzeIncident() throws Exception {
        UUID id = UUID.randomUUID();
        ReportResponse report = ReportResponse.builder()
                .id(UUID.randomUUID())
                .incidentId(id)
                .rootCause("Database connection pool exhausted")
                .confidenceScore(85)
                .evidence(List.of(EvidenceItem.builder().text("Connection timeout in logs").build()))
                .recommendations(List.of(Recommendation.builder().action("Increase pool size").build()))
                .createdAt(LocalDateTime.now())
                .build();

        when(investigationService.analyzeIncident(id)).thenReturn(report);

        mockMvc.perform(post("/api/incidents/{id}/analyze", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause").value("Database connection pool exhausted"))
                .andExpect(jsonPath("$.confidenceScore").value(85));
    }

    @Test
    @DisplayName("GET /api/incidents/{id}/report - should return report")
    void shouldReturnReport() throws Exception {
        UUID id = UUID.randomUUID();
        ReportResponse report = ReportResponse.builder()
                .id(UUID.randomUUID())
                .incidentId(id)
                .rootCause("Memory leak in worker thread")
                .confidenceScore(72)
                .evidence(List.of(EvidenceItem.builder().text("OOM errors").build()))
                .recommendations(List.of(Recommendation.builder().action("Fix memory leak").build()))
                .createdAt(LocalDateTime.now())
                .build();

        when(investigationService.getReport(id)).thenReturn(report);

        mockMvc.perform(get("/api/incidents/{id}/report", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause").value("Memory leak in worker thread"));
    }
}
