package com.example.incidentinvestigator.repository;

import com.example.incidentinvestigator.entity.InvestigationReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link InvestigationReport} entities.
 *
 * <p>Provides standard CRUD operations and a lookup method to
 * retrieve a report by its associated incident ID.</p>
 */
@Repository
public interface InvestigationReportRepository extends JpaRepository<InvestigationReport, UUID> {

    /**
     * Finds the investigation report associated with the given incident.
     *
     * @param incidentId the UUID of the incident
     * @return optional containing the report if one exists
     */
    Optional<InvestigationReport> findByIncidentId(UUID incidentId);

    /**
     * Checks whether a report exists for the given incident.
     *
     * @param incidentId the UUID of the incident
     * @return true if a report exists
     */
    boolean existsByIncidentId(UUID incidentId);

    /**
     * Retrieves the most recent investigation reports.
     * Useful for providing historical context to the LLM.
     *
     * @return list of up to 5 recent reports
     */
    List<InvestigationReport> findTop5ByOrderByCreatedAtDesc();
}
