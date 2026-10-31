package com.example.incidentinvestigator.knowledge.advanced;

import com.example.incidentinvestigator.knowledge.RetrievalResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Computes standard Information Retrieval metrics (Recall@K, MRR, NDCG@K)
 * to evaluate and benchmark RAG retrieval performance.
 */
@Component
public class RetrievalEvaluator {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Metrics {
        private double recallAtK;
        private double mrr;
        private double ndcgAtK;
        private int k;
        private int retrievedCount;
        private int relevantFound;
    }

    /**
     * Evaluates retrieved results against ground-truth relevant document titles or chunk IDs.
     *
     * @param retrieved the ranked list of retrieved results
     * @param groundTruthRelevantTitles set of document titles known to be relevant
     * @param k cutoff rank
     * @return Metrics object with Recall@K, MRR, and NDCG@K
     */
    public Metrics evaluate(List<RetrievalResult> retrieved, Set<String> groundTruthRelevantTitles, int k) {
        if (retrieved == null || retrieved.isEmpty() || groundTruthRelevantTitles == null || groundTruthRelevantTitles.isEmpty()) {
            return Metrics.builder().k(k).retrievedCount(0).relevantFound(0).build();
        }

        int cutoff = Math.min(k, retrieved.size());
        List<RetrievalResult> topK = retrieved.subList(0, cutoff);

        int relevantFound = 0;
        double mrr = 0.0;
        double dcg = 0.0;

        for (int rank = 1; rank <= topK.size(); rank++) {
            RetrievalResult result = topK.get(rank - 1);
            boolean isRelevant = groundTruthRelevantTitles.contains(result.documentTitle());

            if (isRelevant) {
                relevantFound++;
                if (mrr == 0.0) {
                    mrr = 1.0 / rank;
                }
                dcg += 1.0 / (Math.log(rank + 1) / Math.log(2));
            }
        }

        // Ideal DCG
        double idcg = 0.0;
        int maxRelevantInTopK = Math.min(cutoff, groundTruthRelevantTitles.size());
        for (int rank = 1; rank <= maxRelevantInTopK; rank++) {
            idcg += 1.0 / (Math.log(rank + 1) / Math.log(2));
        }

        double ndcg = idcg > 0 ? (dcg / idcg) : 0.0;
        double recall = (double) relevantFound / groundTruthRelevantTitles.size();

        return Metrics.builder()
                .recallAtK(Math.round(recall * 1000.0) / 1000.0)
                .mrr(Math.round(mrr * 1000.0) / 1000.0)
                .ndcgAtK(Math.round(ndcg * 1000.0) / 1000.0)
                .k(cutoff)
                .retrievedCount(topK.size())
                .relevantFound(relevantFound)
                .build();
    }
}
