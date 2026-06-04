package com.example.incidentinvestigator.service;

import com.example.incidentinvestigator.dto.CreateIncidentRequest;
import com.example.incidentinvestigator.dto.IncidentResponse;
import com.example.incidentinvestigator.dto.IncidentSummaryResponse;
import com.example.incidentinvestigator.entity.Incident;
import com.example.incidentinvestigator.exception.ResourceNotFoundException;
import com.example.incidentinvestigator.mapper.IncidentMapper;
import com.example.incidentinvestigator.repository.IncidentRepository;
import com.example.incidentinvestigator.repository.InvestigationReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for incident management.
 *
 * <p>Handles CRUD operations for incidents. This service does NOT
 * handle AI analysis — that responsibility belongs to
 * {@link InvestigationService}.</p>
 */
@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidentRepository;
    private final InvestigationReportRepository reportRepository;
    private final IncidentMapper mapper;

    public IncidentService(IncidentRepository incidentRepository,
                           InvestigationReportRepository reportRepository,
                           IncidentMapper mapper) {
        this.incidentRepository = incidentRepository;
        this.reportRepository = reportRepository;
        this.mapper = mapper;
    }

    /**
     * Creates a new incident from the given request.
     *
     * @param request the incident creation data
     * @return the created incident response with generated UUID
     */
    @Transactional
    public IncidentResponse createIncident(CreateIncidentRequest request) {
        log.info("Creating new incident: {}", request.getTitle());

        Incident incident = mapper.toEntity(request);
        Incident saved = incidentRepository.save(incident);

        log.info("Incident created with id: {}", saved.getId());
        return mapper.toResponse(saved);
    }

    /**
     * Retrieves a single incident by its ID.
     *
     * @param id the incident UUID
     * @return the full incident response
     * @throws ResourceNotFoundException if no incident exists with the given ID
     */
    @Transactional(readOnly = true)
    public IncidentResponse getIncident(UUID id) {
        log.debug("Fetching incident: {}", id);

        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));

        return mapper.toResponse(incident);
    }

    /**
     * Retrieves incidents matching the given filters, as summary DTOs, ordered by creation date descending.
     */
    @Transactional(readOnly = true)
    public List<IncidentSummaryResponse> getAllIncidents(String severity, List<String> tags, String search, LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Fetching incidents with filters");

        Specification<Incident> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            Join<Incident, com.example.incidentinvestigator.entity.InvestigationReport> reportJoin = root.join("report", JoinType.LEFT);

            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(reportJoin.get("severity"), severity));
            }
            if (tags != null && !tags.isEmpty()) {
                for (String tag : tags) {
                    predicates.add(cb.like(reportJoin.get("tags"), "%" + tag + "%"));
                }
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + search.toLowerCase() + "%"));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            
            query.orderBy(cb.desc(root.get("createdAt")));
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return incidentRepository.findAll(spec).stream()
                .map(incident -> {
                    boolean hasReport = incident.getReport() != null;
                    return mapper.toSummaryResponse(incident, hasReport);
                })
                .collect(Collectors.toList());
    }
}
