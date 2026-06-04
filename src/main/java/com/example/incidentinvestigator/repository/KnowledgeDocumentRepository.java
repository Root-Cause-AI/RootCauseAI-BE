package com.example.incidentinvestigator.repository;

import com.example.incidentinvestigator.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link KnowledgeDocument} entities.
 */
@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    List<KnowledgeDocument> findByDocumentTypeOrderByCreatedAtDesc(String documentType);

    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();

    List<KnowledgeDocument> findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(String title);

    long countByDocumentType(String documentType);
}
