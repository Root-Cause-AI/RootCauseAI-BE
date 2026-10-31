package com.example.incidentinvestigator.knowledge.advanced;

import com.example.incidentinvestigator.knowledge.RetrievalResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RetrievalEvaluator Tests")
class RetrievalEvaluatorTest {

    private final RetrievalEvaluator evaluator = new RetrievalEvaluator();

    @Test
    @DisplayName("should compute perfect Recall@K, MRR, and NDCG when top result is ground truth")
    void shouldComputePerfectMetricsForTopRankMatch() {
        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();

        List<RetrievalResult> retrieved = List.of(
                new RetrievalResult(UUID.randomUUID(), docId1, "Postgres Runbook", "RUNBOOK", "content", 0.95),
                new RetrievalResult(UUID.randomUUID(), docId2, "Redis Guide", "RUNBOOK", "content", 0.80)
        );

        Set<String> groundTruth = Set.of("Postgres Runbook");

        RetrievalEvaluator.Metrics metrics = evaluator.evaluate(retrieved, groundTruth, 5);

        assertThat(metrics.getRecallAtK()).isEqualTo(1.0);
        assertThat(metrics.getMrr()).isEqualTo(1.0);
        assertThat(metrics.getNdcgAtK()).isEqualTo(1.0);
        assertThat(metrics.getRelevantFound()).isEqualTo(1);
    }

    @Test
    @DisplayName("should compute MRR=0.5 when relevant result is at rank 2")
    void shouldComputeCorrectMRRForSecondRank() {
        List<RetrievalResult> retrieved = List.of(
                new RetrievalResult(UUID.randomUUID(), UUID.randomUUID(), "Kafka Runbook", "RUNBOOK", "content", 0.95),
                new RetrievalResult(UUID.randomUUID(), UUID.randomUUID(), "Postgres Runbook", "RUNBOOK", "content", 0.80)
        );

        Set<String> groundTruth = Set.of("Postgres Runbook");

        RetrievalEvaluator.Metrics metrics = evaluator.evaluate(retrieved, groundTruth, 5);

        assertThat(metrics.getRecallAtK()).isEqualTo(1.0);
        assertThat(metrics.getMrr()).isEqualTo(0.5);
        assertThat(metrics.getRelevantFound()).isEqualTo(1);
    }

    @Test
    @DisplayName("should return zeros when no relevant document found")
    void shouldHandleNoRelevantFound() {
        List<RetrievalResult> retrieved = List.of(
                new RetrievalResult(UUID.randomUUID(), UUID.randomUUID(), "Kafka Runbook", "RUNBOOK", "content", 0.95)
        );

        Set<String> groundTruth = Set.of("Postgres Runbook");

        RetrievalEvaluator.Metrics metrics = evaluator.evaluate(retrieved, groundTruth, 5);

        assertThat(metrics.getRecallAtK()).isEqualTo(0.0);
        assertThat(metrics.getMrr()).isEqualTo(0.0);
        assertThat(metrics.getNdcgAtK()).isEqualTo(0.0);
        assertThat(metrics.getRelevantFound()).isEqualTo(0);
    }
}
