package com.example.incidentinvestigator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing a text chunk from a knowledge document.
 *
 * <p>Each chunk contains a portion of a document's text along with
 * its vector embedding (768-dim from text-embedding-004) for semantic
 * search and a tsvector for keyword search.</p>
 *
 * <p>The embedding field is stored as a pgvector column but mapped
 * as a String in JPA since Hibernate doesn't natively support the
 * vector type. All similarity queries use native SQL.</p>
 */
@Entity
@Table(name = "knowledge_chunks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * The embedding is handled via native queries, not through JPA mapping,
     * because Hibernate doesn't support the pgvector type natively.
     * This field is excluded from JPA column mapping.
     */
    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
