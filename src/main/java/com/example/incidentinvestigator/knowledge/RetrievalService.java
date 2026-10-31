package com.example.incidentinvestigator.knowledge;

import com.example.incidentinvestigator.entity.KnowledgeDocument;
import com.example.incidentinvestigator.repository.KnowledgeChunkRepository;
import com.example.incidentinvestigator.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hybrid retrieval service combining semantic and keyword search
 * with Reciprocal Rank Fusion (RRF) for combined ranking.
 *
 * <p>The retrieval pipeline:</p>
 * <ol>
 *   <li>Embed the query text → semantic search via pgvector cosine distance</li>
 *   <li>Run keyword search via PostgreSQL full-text search (tsvector/tsquery)</li>
 *   <li>Merge results using RRF: score = Σ 1/(k + rank)</li>
 *   <li>Return top-K ranked results with document metadata</li>
 * </ol>
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);
    private static final int DEFAULT_TOP_K = 10;
    private static final int RRF_K = 60; // Standard RRF constant

    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final com.example.incidentinvestigator.knowledge.advanced.FullTextQuerySanitizer querySanitizer;
    private final com.example.incidentinvestigator.knowledge.advanced.QueryDecomposer queryDecomposer;
    private final com.example.incidentinvestigator.knowledge.advanced.RelevanceReranker relevanceReranker;

    public RetrievalService(KnowledgeChunkRepository chunkRepository,
                            KnowledgeDocumentRepository documentRepository,
                            EmbeddingService embeddingService,
                            com.example.incidentinvestigator.knowledge.advanced.FullTextQuerySanitizer querySanitizer,
                            com.example.incidentinvestigator.knowledge.advanced.QueryDecomposer queryDecomposer,
                            com.example.incidentinvestigator.knowledge.advanced.RelevanceReranker relevanceReranker) {
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.embeddingService = embeddingService;
        this.querySanitizer = querySanitizer;
        this.queryDecomposer = queryDecomposer;
        this.relevanceReranker = relevanceReranker;
    }

    /**
     * Retrieves the most relevant knowledge chunks for a query.
     * Uses hybrid search (semantic + keyword) with RRF fusion.
     *
     * @param query the search query (incident title + logs excerpt)
     * @return ranked list of retrieval results
     */
    @Transactional(readOnly = true)
    public List<RetrievalResult> retrieve(String query) {
        return retrieve(query, DEFAULT_TOP_K);
    }

    /**
     * Retrieves the most relevant knowledge chunks for a query.
     *
     * @param query the search query
     * @param topK  maximum number of results to return
     * @return ranked list of retrieval results
     */
    @Transactional(readOnly = true)
    public List<RetrievalResult> retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // Check if there are any documents at all
        if (documentRepository.count() == 0) {
            log.debug("No knowledge documents available, skipping retrieval");
            return List.of();
        }

        log.info("Performing hybrid retrieval for query (length: {} chars)", query.length());

        // 1. Semantic search
        List<SemanticHit> semanticHits = performSemanticSearch(query, topK * 2);

        // 2. Keyword search
        List<KeywordHit> keywordHits = performKeywordSearch(query, topK * 2);

        // 3. RRF fusion
        List<RetrievalResult> fused = fuseResults(semanticHits, keywordHits, topK);

        log.info("Hybrid retrieval returned {} results (semantic: {}, keyword: {})",
                fused.size(), semanticHits.size(), keywordHits.size());

        return fused;
    }

    /**
     * Advanced retrieval pipeline with query decomposition, metadata filtering,
     * hybrid RRF fusion, and LLM relevance reranking.
     */
    @Transactional(readOnly = true)
    public List<RetrievalResult> retrieveAdvanced(String query, int topK, String docTypeFilter, boolean enableRerank) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<String> subQueries = queryDecomposer.decompose(query);
        Map<UUID, RetrievalResult> aggregated = new HashMap<>();

        for (String subQuery : subQueries) {
            List<RetrievalResult> hits = retrieve(subQuery, topK * 2);
            for (RetrievalResult r : hits) {
                if (docTypeFilter != null && !docTypeFilter.isBlank() && !docTypeFilter.equalsIgnoreCase(r.documentType())) {
                    continue;
                }
                aggregated.putIfAbsent(r.chunkId(), r);
            }
        }

        List<RetrievalResult> candidates = new ArrayList<>(aggregated.values());
        if (enableRerank && !candidates.isEmpty()) {
            return relevanceReranker.rerank(query, candidates, topK);
        }

        return candidates.stream().limit(topK).toList();
    }

    private List<SemanticHit> performSemanticSearch(String query, int limit) {
        try {
            float[] queryEmbedding = embeddingService.embed(query);
            String embeddingStr = EmbeddingService.toVectorString(queryEmbedding);

            List<Object[]> rows = chunkRepository.findSimilarByEmbedding(embeddingStr, limit);
            List<SemanticHit> hits = new ArrayList<>();

            for (Object[] row : rows) {
                UUID chunkId = (UUID) row[0];
                UUID documentId = (UUID) row[1];
                String content = (String) row[3];
                double distance = ((Number) row[6]).doubleValue();
                // Convert cosine distance to similarity (1 - distance)
                double similarity = 1.0 - distance;
                hits.add(new SemanticHit(chunkId, documentId, content, similarity));
            }

            log.debug("Semantic search returned {} hits", hits.size());
            return hits;
        } catch (Exception e) {
            log.warn("Semantic search failed, falling back to keyword-only: {}", e.getMessage());
            return List.of();
        }
    }

    private List<KeywordHit> performKeywordSearch(String query, int limit) {
        try {
            String sanitized = querySanitizer.sanitizeForPlainTsQuery(query);
            if (sanitized.isBlank()) {
                sanitized = query;
            }
            List<Object[]> rows = chunkRepository.findByKeyword(sanitized, limit);
            List<KeywordHit> hits = new ArrayList<>();

            for (Object[] row : rows) {
                UUID chunkId = (UUID) row[0];
                UUID documentId = (UUID) row[1];
                String content = (String) row[3];
                double rank = ((Number) row[6]).doubleValue();
                hits.add(new KeywordHit(chunkId, documentId, content, rank));
            }

            log.debug("Keyword search returned {} hits (sanitized query: '{}')", hits.size(), sanitized);
            return hits;
        } catch (Exception e) {
            log.warn("Keyword search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Merges semantic and keyword results using Reciprocal Rank Fusion.
     * RRF score = sum of 1/(k + rank) across all lists.
     */
    private List<RetrievalResult> fuseResults(List<SemanticHit> semanticHits,
                                               List<KeywordHit> keywordHits,
                                               int topK) {
        Map<UUID, Double> rrfScores = new HashMap<>();
        Map<UUID, UUID> chunkToDoc = new HashMap<>();
        Map<UUID, String> chunkToContent = new HashMap<>();

        // Score semantic hits
        for (int i = 0; i < semanticHits.size(); i++) {
            SemanticHit hit = semanticHits.get(i);
            double score = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(hit.chunkId, score, Double::sum);
            chunkToDoc.put(hit.chunkId, hit.documentId);
            chunkToContent.put(hit.chunkId, hit.content);
        }

        // Score keyword hits
        for (int i = 0; i < keywordHits.size(); i++) {
            KeywordHit hit = keywordHits.get(i);
            double score = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(hit.chunkId, score, Double::sum);
            chunkToDoc.put(hit.chunkId, hit.documentId);
            chunkToContent.put(hit.chunkId, hit.content);
        }

        // Build results sorted by RRF score
        // Cache document lookups
        Map<UUID, KnowledgeDocument> docCache = new HashMap<>();

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    UUID chunkId = entry.getKey();
                    UUID docId = chunkToDoc.get(chunkId);
                    KnowledgeDocument doc = docCache.computeIfAbsent(docId,
                            id -> documentRepository.findById(id).orElse(null));

                    return new RetrievalResult(
                            chunkId,
                            docId,
                            doc != null ? doc.getTitle() : "Unknown",
                            doc != null ? doc.getDocumentType() : "UNKNOWN",
                            chunkToContent.get(chunkId),
                            entry.getValue()
                    );
                })
                .toList();
    }

    private record SemanticHit(UUID chunkId, UUID documentId, String content, double similarity) {}
    private record KeywordHit(UUID chunkId, UUID documentId, String content, double rank) {}
}
