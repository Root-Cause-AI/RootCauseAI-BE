package com.example.incidentinvestigator.service;

import com.example.incidentinvestigator.dto.CreateIncidentRequest;
import com.example.incidentinvestigator.dto.IncidentResponse;
import com.example.incidentinvestigator.dto.IncidentSummaryResponse;
import com.example.incidentinvestigator.entity.Incident;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentService Tests")
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private InvestigationReportRepository reportRepository;

    @Mock
    private IncidentMapper mapper;

    @InjectMocks
    private IncidentService incidentService;

    private UUID testId;
    private Incident testIncident;
    private IncidentResponse testResponse;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testIncident = Incident.builder()
                .id(testId)
                .title("Payment Service Failure")
                .logs("ERROR: Connection timeout")
                .deploymentInfo("version: 2.1.0")
                .createdAt(LocalDateTime.now())
                .build();

        testResponse = IncidentResponse.builder()
                .id(testId)
                .title("Payment Service Failure")
                .logs("ERROR: Connection timeout")
                .deploymentInfo("version: 2.1.0")
                .createdAt(testIncident.getCreatedAt())
                .hasReport(false)
                .build();
    }

    @Nested
    @DisplayName("createIncident")
    class CreateIncident {

        @Test
        @DisplayName("should create and return incident")
        void shouldCreateIncident() {
            CreateIncidentRequest request = CreateIncidentRequest.builder()
                    .title("Payment Service Failure")
                    .logs("ERROR: Connection timeout")
                    .deploymentInfo("version: 2.1.0")
                    .build();

            when(mapper.toEntity(request)).thenReturn(testIncident);
            when(incidentRepository.save(any(Incident.class))).thenReturn(testIncident);
            when(mapper.toResponse(testIncident)).thenReturn(testResponse);

            IncidentResponse result = incidentService.createIncident(request);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testId);
            assertThat(result.getTitle()).isEqualTo("Payment Service Failure");
            verify(incidentRepository).save(any(Incident.class));
        }
    }

    @Nested
    @DisplayName("getIncident")
    class GetIncident {

        @Test
        @DisplayName("should return incident when found")
        void shouldReturnIncidentWhenFound() {
            when(incidentRepository.findById(testId)).thenReturn(Optional.of(testIncident));
            when(mapper.toResponse(testIncident)).thenReturn(testResponse);

            IncidentResponse result = incidentService.getIncident(testId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testId);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            UUID missingId = UUID.randomUUID();
            when(incidentRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> incidentService.getIncident(missingId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(missingId.toString());
        }
    }

    @Nested
    @DisplayName("getAllIncidents")
    class GetAllIncidents {

        @Test
        @DisplayName("should return all incidents as summaries")
        void shouldReturnAllIncidents() {
            IncidentSummaryResponse summary = IncidentSummaryResponse.builder()
                    .id(testId)
                    .title("Payment Service Failure")
                    .createdAt(testIncident.getCreatedAt())
                    .hasReport(false)
                    .build();

            when(incidentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                    .thenReturn(List.of(testIncident));
            when(mapper.toSummaryResponse(testIncident, false)).thenReturn(summary);

            List<IncidentSummaryResponse> results = incidentService.getAllIncidents(null, null, null, null, null);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle()).isEqualTo("Payment Service Failure");
        }

        @Test
        @DisplayName("should return empty list when no incidents exist")
        void shouldReturnEmptyList() {
            when(incidentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                    .thenReturn(List.of());

            List<IncidentSummaryResponse> results = incidentService.getAllIncidents(null, null, null, null, null);

            assertThat(results).isEmpty();
        }
    }
}
