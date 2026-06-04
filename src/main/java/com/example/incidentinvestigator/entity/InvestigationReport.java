package com.example.incidentinvestigator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
 * JPA entity representing an AI-generated investigation report.
 *
 * <p>Each report is linked to exactly one incident and contains the
 * structured output from AI analysis: root cause, confidence score,
 * supporting evidence, and recommended actions.</p>
 *
 * <p>Evidence and recommendations are stored as JSON arrays in TEXT
 * columns and converted to/from {@code List<String>} at the DTO layer.</p>
 */
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestigationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false, unique = true)
    private Incident incident;

    @Column(name = "root_cause", nullable = false, columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "confidence_score", nullable = false)
    private Integer confidenceScore;

    @Column(name = "evidence", nullable = false, columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "recommendations", nullable = false, columnDefinition = "TEXT")
    private String recommendations;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "executive_summary", columnDefinition = "TEXT")
    private String executiveSummary;

    @Column(name = "engineering_summary", columnDefinition = "TEXT")
    private String engineeringSummary;

    @Column(name = "timeline", columnDefinition = "TEXT")
    private String timeline;

    @Column(name = "similar_incidents", columnDefinition = "TEXT")
    private String similarIncidents;

    @Column(name = "rag_context", columnDefinition = "TEXT")
    private String ragContext;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "citations", columnDefinition = "JSONB")
    private String citations;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
