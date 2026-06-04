package com.example.incidentinvestigator.repository;

import com.example.incidentinvestigator.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Incident} entities.
 *
 * <p>Provides standard CRUD operations and a custom query method
 * for listing incidents in reverse chronological order.</p>
 */
@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    /**
     * Retrieves all incidents ordered by creation date descending.
     *
     * @return list of incidents, newest first
     */
    List<Incident> findAllByOrderByCreatedAtDesc();
}
