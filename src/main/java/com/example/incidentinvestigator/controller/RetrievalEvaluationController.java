package com.example.incidentinvestigator.controller;

import com.example.incidentinvestigator.knowledge.RetrievalResult;
import com.example.incidentinvestigator.knowledge.RetrievalService;
import com.example.incidentinvestigator.knowledge.advanced.RetrievalEvaluator;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controller for running empirical evaluations on the RAG retrieval pipeline,
 * measuring Recall@K, MRR, and NDCG@K against expected relevance targets.
 */
@RestController
@RequestMapping("/api/knowledge")
public class RetrievalEvaluationController {

    private static final Logger log = LoggerFactory.getLogger(RetrievalEvaluationController.class);

    private final RetrievalService retrievalService;
    private final RetrievalEvaluator evaluator;

    public RetrievalEvaluationController(RetrievalService retrievalService,
                                         RetrievalEvaluator evaluator) {
        this.retrievalService = retrievalService;
        this.evaluator = evaluator;
    }

    @Getter
    @Setter
    public static class EvaluationRequest {
        private String query;
        private List<String> expectedDocumentTitles;
        private int k = 5;
        private boolean useAdvanced = true;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateRetrieval(@RequestBody EvaluationRequest request) {
        log.info("POST /api/knowledge/evaluate - query='{}', k={}, useAdvanced={}",
                request.getQuery(), request.getK(), request.isUseAdvanced());

        List<RetrievalResult> results = request.isUseAdvanced()
                ? retrievalService.retrieveAdvanced(request.getQuery(), request.getK(), null, true)
                : retrievalService.retrieve(request.getQuery(), request.getK());

        Set<String> groundTruth = new HashSet<>(
                request.getExpectedDocumentTitles() != null ? request.getExpectedDocumentTitles() : List.of()
        );

        RetrievalEvaluator.Metrics metrics = evaluator.evaluate(results, groundTruth, request.getK());

        return ResponseEntity.ok(Map.of(
                "query", request.getQuery(),
                "metrics", metrics,
                "retrieved", results.stream().map(r -> Map.of(
                        "documentTitle", r.documentTitle(),
                        "documentType", r.documentType(),
                        "score", r.relevanceScore()
                )).toList()
        ));
    }
}
