package com.example.incidentinvestigator.repository;

import com.example.incidentinvestigator.entity.InvestigationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link InvestigationJob} entities.
 */
@Repository
public interface InvestigationJobRepository extends JpaRepository<InvestigationJob, UUID> {

    Optional<InvestigationJob> findTopByIncidentIdOrderByCreatedAtDesc(UUID incidentId);

    List<InvestigationJob> findAllByIncidentIdOrderByCreatedAtDesc(UUID incidentId);

    boolean existsByIncidentIdAndStatus(UUID incidentId, InvestigationJob.Status status);
}
