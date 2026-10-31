package com.example.incidentinvestigator.knowledge.advanced;

import com.example.incidentinvestigator.knowledge.RetrievalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reranks candidate retrieved knowledge chunks using LLM-based relevance scoring.
 */
@Component
public class RelevanceReranker {

    private static final Logger log = LoggerFactory.getLogger(RelevanceReranker.class);
    private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d{1,2})");

    private final ChatClient chatClient;

    public RelevanceReranker(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Reranks the given candidate chunks according to relevance against the query.
     *
     * @param query the incident query
     * @param candidates initial list of candidate retrieval results from RRF
     * @param topK the final number of results desired
     * @return reranked results list
     */
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        if (candidates.size() <= 2) {
            return candidates.stream().limit(topK).toList();
        }

        log.info("Reranking {} candidate chunks for query: '{}...'",
                candidates.size(), query.substring(0, Math.min(query.length(), 60)));

        List<ScoredResult> scoredResults = new ArrayList<>();

        for (RetrievalResult chunk : candidates) {
            double score = chunk.relevanceScore(); // base RRF score

            try {
                String prompt = String.format("""
                        Incident Query: %s
                        
                        Knowledge Document Chunk:
                        Title: %s
                        Content: %s
                        
                        Rate the technical relevance of this chunk for diagnosing or resolving the incident query on a scale of 0 to 10.
                        Output ONLY an integer between 0 and 10.
                        """,
                        query.substring(0, Math.min(query.length(), 300)),
                        chunk.documentTitle(),
                        chunk.content() != null && chunk.content().length() > 400
                                ? chunk.content().substring(0, 400)
                                : chunk.content());

                String resp = chatClient.prompt()
                        .system("You are an SRE relevance evaluation model. Return only an integer 0-10.")
                        .user(prompt)
                        .call()
                        .content();

                if (resp != null) {
                    Matcher m = SCORE_PATTERN.matcher(resp.trim());
                    if (m.find()) {
                        int llmScore = Integer.parseInt(m.group(1));
                        // Blend RRF score and LLM relevance: 0.3 * RRF + 0.7 * (llmScore / 10.0)
                        score = (0.3 * score) + (0.7 * (llmScore / 10.0));
                    }
                }
            } catch (Exception e) {
                log.debug("LLM reranking score skipped for chunk {}: {}", chunk.chunkId(), e.getMessage());
            }

            scoredResults.add(new ScoredResult(chunk, score));
        }

        return scoredResults.stream()
                .sorted(Comparator.comparingDouble(ScoredResult::score).reversed())
                .limit(topK)
                .map(sr -> new RetrievalResult(
                        sr.result().chunkId(),
                        sr.result().documentId(),
                        sr.result().documentTitle(),
                        sr.result().documentType(),
                        sr.result().content(),
                        sr.score()
                ))
                .toList();
    }

    private record ScoredResult(RetrievalResult result, double score) {}
}
