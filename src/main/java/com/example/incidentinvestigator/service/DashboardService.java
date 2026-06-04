package com.example.incidentinvestigator.service;

import com.example.incidentinvestigator.dto.DashboardStatsResponse;
import com.example.incidentinvestigator.entity.Incident;
import com.example.incidentinvestigator.entity.InvestigationReport;
import com.example.incidentinvestigator.repository.IncidentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final IncidentRepository incidentRepository;
    private final ObjectMapper objectMapper;
    private final KnowledgeService knowledgeService;

    public DashboardService(IncidentRepository incidentRepository, ObjectMapper objectMapper, KnowledgeService knowledgeService) {
        this.incidentRepository = incidentRepository;
        this.objectMapper = objectMapper;
        this.knowledgeService = knowledgeService;
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        List<Incident> incidents = incidentRepository.findAll();
        
        long total = incidents.size();
        long critical = 0;
        long high = 0;
        long medium = 0;
        long low = 0;
        
        Map<String, Long> tagDistribution = new HashMap<>();
        Map<String, Long> trend = new HashMap<>();
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (Incident incident : incidents) {
            String dateKey = incident.getCreatedAt().format(formatter);
            trend.put(dateKey, trend.getOrDefault(dateKey, 0L) + 1);

            InvestigationReport report = incident.getReport();
            if (report != null) {
                if ("CRITICAL".equalsIgnoreCase(report.getSeverity())) critical++;
                else if ("HIGH".equalsIgnoreCase(report.getSeverity())) high++;
                else if ("MEDIUM".equalsIgnoreCase(report.getSeverity())) medium++;
                else if ("LOW".equalsIgnoreCase(report.getSeverity())) low++;

                if (report.getTags() != null && !report.getTags().isBlank()) {
                    try {
                        List<String> tags = objectMapper.readValue(report.getTags(), new TypeReference<List<String>>() {});
                        for (String tag : tags) {
                            tagDistribution.put(tag, tagDistribution.getOrDefault(tag, 0L) + 1);
                        }
                    } catch (Exception e) {
                        // Ignore parsing errors for stats
                    }
                }
            }
        }

        return DashboardStatsResponse.builder()
                .totalIncidents(total)
                .criticalIncidents(critical)
                .highIncidents(high)
                .mediumIncidents(medium)
                .lowIncidents(low)
                .tagDistribution(tagDistribution)
                .incidentTrend(trend)
                .knowledgeStats(knowledgeService.getStats())
                .build();
    }
}
