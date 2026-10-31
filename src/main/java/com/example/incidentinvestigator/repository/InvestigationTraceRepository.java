package com.example.incidentinvestigator.repository;

import com.example.incidentinvestigator.entity.InvestigationTrace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvestigationTraceRepository extends JpaRepository<InvestigationTrace, UUID> {

    List<InvestigationTrace> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

    List<InvestigationTrace> findByJobIdOrderByCreatedAtAsc(UUID jobId);
}
