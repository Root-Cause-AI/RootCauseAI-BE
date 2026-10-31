package com.example.incidentinvestigator.knowledge.advanced;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sanitizes and formats raw query strings into optimal PostgreSQL
 * tsquery expressions, handling punctuation, special characters, and stacktraces.
 */
@Component
public class FullTextQuerySanitizer {

    private static final Pattern SPECIAL_CHARS = Pattern.compile("[^a-zA-Z0-9_\\s]");

    /**
     * Sanitizes a query string for use with PostgreSQL full-text search.
     * Strips punctuation, isolates significant words, and limits length.
     *
     * @param rawQuery input text which may contain punctuation and symbols
     * @return clean space-separated terms suitable for plainto_tsquery
     */
    public String sanitizeForPlainTsQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }

        // Replace special characters with spaces
        String cleaned = SPECIAL_CHARS.matcher(rawQuery).replaceAll(" ");

        // Remove extra spaces and keep tokens of length >= 2
        return Arrays.stream(cleaned.split("\\s+"))
                .filter(term -> term.length() >= 2)
                .limit(20) // Limit to top 20 keywords to keep tsquery efficient
                .collect(Collectors.joining(" "));
    }
}
