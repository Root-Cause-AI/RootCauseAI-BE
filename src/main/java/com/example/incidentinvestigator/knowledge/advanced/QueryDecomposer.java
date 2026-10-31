package com.example.incidentinvestigator.knowledge.advanced;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decomposes complex incident queries containing logs, error messages,
 * and deployment contexts into distinct, focused sub-queries for RAG retrieval.
 */
@Component
public class QueryDecomposer {

    private static final Logger log = LoggerFactory.getLogger(QueryDecomposer.class);
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("([a-zA-Z0-9_.]+(?:Exception|Error|Timeout|Fault))");

    private final ChatClient chatClient;

    public QueryDecomposer(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Decomposes a query into multiple targeted search queries.
     * Combines fast rule-based extraction with LLM query expansion.
     *
     * @param query the raw incident query or log excerpt
     * @return list of focused sub-queries
     */
    public List<String> decompose(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<String> subQueries = new ArrayList<>();
        subQueries.add(query.length() > 200 ? query.substring(0, 200) : query);

        // Rule-based extraction of specific exception names
        Matcher matcher = EXCEPTION_PATTERN.matcher(query);
        while (matcher.find() && subQueries.size() < 3) {
            String ex = matcher.group(1);
            if (!subQueries.contains(ex)) {
                subQueries.add(ex);
            }
        }

        // For long complex queries, optionally ask LLM for 2 focused retrieval queries
        if (query.length() > 300) {
            try {
                String prompt = String.format("""
                        Given this production incident description/log excerpt:
                        %s
                        
                        Generate exactly 2 concise, focused technical search queries (max 6 words each)
                        to search runbooks and incident knowledge bases.
                        Output each query on a new line, without numbering or bullets.
                        """, query.substring(0, Math.min(query.length(), 1500)));

                String response = chatClient.prompt()
                        .system("You are a technical search query optimizer. Return only 2 lines of text.")
                        .user(prompt)
                        .call()
                        .content();

                if (response != null) {
                    for (String line : response.split("\\R")) {
                        String trimmed = line.replaceAll("^[-*0-9.]+\\s*", "").trim();
                        if (!trimmed.isBlank() && !subQueries.contains(trimmed)) {
                            subQueries.add(trimmed);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("LLM query decomposition failed, using rule-based queries: {}", e.getMessage());
            }
        }

        log.info("Query decomposed into {} sub-queries: {}", subQueries.size(), subQueries);
        return subQueries;
    }
}
