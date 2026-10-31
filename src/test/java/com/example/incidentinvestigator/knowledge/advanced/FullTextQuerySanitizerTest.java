package com.example.incidentinvestigator.knowledge.advanced;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FullTextQuerySanitizer Tests")
class FullTextQuerySanitizerTest {

    private final FullTextQuerySanitizer sanitizer = new FullTextQuerySanitizer();

    @Test
    @DisplayName("should strip stack trace punctuation and keep alphanumeric words")
    void shouldSanitizeStackTracePunctuation() {
        String raw = "org.postgresql.util.PSQLException: Connection to localhost:5432 refused (connection timed out)";
        String sanitized = sanitizer.sanitizeForPlainTsQuery(raw);

        assertThat(sanitized).contains("org postgresql util PSQLException Connection to localhost 5432 refused connection timed out");
        assertThat(sanitized).doesNotContain(":");
        assertThat(sanitized).doesNotContain("(");
        assertThat(sanitized).doesNotContain(")");
        assertThat(sanitized).doesNotContain(".");
    }

    @Test
    @DisplayName("should return empty string for null or blank input")
    void shouldHandleNullOrBlank() {
        assertThat(sanitizer.sanitizeForPlainTsQuery(null)).isEmpty();
        assertThat(sanitizer.sanitizeForPlainTsQuery("   ")).isEmpty();
    }
}
