package com.example.incidentinvestigator.ai;

import com.example.incidentinvestigator.dto.AiAnalysisResponse;
import com.example.incidentinvestigator.exception.AnalysisException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AiResponseParser Tests")
class AiResponseParserTest {

    private AiResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiResponseParser(new ObjectMapper());
    }

    @Nested
    @DisplayName("Valid JSON parsing")
    class ValidJson {

        @Test
        @DisplayName("should parse valid JSON response")
        void shouldParseValidJson() {
            String json = """
                    {
                      "rootCause": "Database connection pool exhausted",
                      "confidence": 85,
                      "evidence": [
                        { "text": "Connection timeout errors", "timestamp": "2026-06-03T01:00:00Z", "severity": "HIGH" },
                        { "text": "Pool size at max", "timestamp": "2026-06-03T01:05:00Z", "severity": "MEDIUM" }
                      ],
                      "recommendations": [
                        { "action": "Increase pool size", "priority": "HIGH" },
                        { "action": "Add connection monitoring", "priority": "MEDIUM" }
                      ]
                    }
                    """;

            AiAnalysisResponse result = parser.parse(json);

            assertThat(result.getRootCause()).isEqualTo("Database connection pool exhausted");
            assertThat(result.getConfidence()).isEqualTo(85);
            assertThat(result.getEvidence()).hasSize(2);
            assertThat(result.getRecommendations()).hasSize(2);
        }

        @Test
        @DisplayName("should parse JSON wrapped in markdown code fences")
        void shouldParseMarkdownWrappedJson() {
            String json = """
                    ```json
                    {
                      "rootCause": "Memory leak",
                      "confidence": 72,
                      "evidence": [
                        { "text": "OOM errors", "timestamp": "2026-06-03T01:00:00Z", "severity": "CRITICAL" }
                      ],
                      "recommendations": [
                        { "action": "Fix leak", "priority": "CRITICAL" }
                      ]
                    }
                    ```
                    """;

            AiAnalysisResponse result = parser.parse(json);

            assertThat(result.getRootCause()).isEqualTo("Memory leak");
            assertThat(result.getConfidence()).isEqualTo(72);
        }

        @Test
        @DisplayName("should parse JSON with surrounding text")
        void shouldParseJsonWithSurroundingText() {
            String json = """
                    Here is my analysis:
                    {
                      "rootCause": "Disk space full",
                      "confidence": 90,
                      "evidence": [
                        { "text": "Disk at 100%", "timestamp": "2026-06-03T01:00:00Z", "severity": "CRITICAL" }
                      ],
                      "recommendations": [
                        { "action": "Clear logs", "priority": "HIGH" }
                      ]
                    }
                    Hope this helps!
                    """;

            AiAnalysisResponse result = parser.parse(json);

            assertThat(result.getRootCause()).isEqualTo("Disk space full");
        }
    }

    @Nested
    @DisplayName("Missing fields")
    class MissingFields {

        @Test
        @DisplayName("should apply defaults for missing optional fields")
        void shouldApplyDefaults() {
            String json = """
                    {
                      "rootCause": "Service crashed"
                    }
                    """;

            AiAnalysisResponse result = parser.parse(json);

            assertThat(result.getRootCause()).isEqualTo("Service crashed");
            assertThat(result.getConfidence()).isEqualTo(0);
            assertThat(result.getEvidence()).isEmpty();
            assertThat(result.getRecommendations()).isEmpty();
        }

        @Test
        @DisplayName("should throw when root cause is missing")
        void shouldThrowWhenRootCauseMissing() {
            String json = """
                    {
                      "confidence": 50,
                      "evidence": [
                        { "text": "Error logs", "timestamp": "2026-06-03T01:00:00Z", "severity": "LOW" }
                      ],
                      "recommendations": [
                        { "action": "Restart", "priority": "MEDIUM" }
                      ]
                    }
                    """;

            assertThatThrownBy(() -> parser.parse(json))
                    .isInstanceOf(AnalysisException.class)
                    .hasMessageContaining("root cause");
        }

        @Test
        @DisplayName("should throw when root cause is blank")
        void shouldThrowWhenRootCauseBlank() {
            String json = """
                    {
                      "rootCause": "   ",
                      "confidence": 50
                    }
                    """;

            assertThatThrownBy(() -> parser.parse(json))
                    .isInstanceOf(AnalysisException.class)
                    .hasMessageContaining("root cause");
        }
    }

    @Nested
    @DisplayName("Invalid inputs")
    class InvalidInputs {

        @Test
        @DisplayName("should throw for null response")
        void shouldThrowForNull() {
            assertThatThrownBy(() -> parser.parse(null))
                    .isInstanceOf(AnalysisException.class)
                    .hasMessageContaining("empty response");
        }

        @Test
        @DisplayName("should throw for empty response")
        void shouldThrowForEmpty() {
            assertThatThrownBy(() -> parser.parse(""))
                    .isInstanceOf(AnalysisException.class)
                    .hasMessageContaining("empty response");
        }

        @Test
        @DisplayName("should throw for blank response")
        void shouldThrowForBlank() {
            assertThatThrownBy(() -> parser.parse("   "))
                    .isInstanceOf(AnalysisException.class)
                    .hasMessageContaining("empty response");
        }

        @Test
        @DisplayName("should throw for completely invalid JSON")
        void shouldThrowForInvalidJson() {
            assertThatThrownBy(() -> parser.parse("this is not json at all"))
                    .isInstanceOf(AnalysisException.class)
                    .hasMessageContaining("Failed to parse");
        }

        @Test
        @DisplayName("should throw for malformed JSON")
        void shouldThrowForMalformedJson() {
            assertThatThrownBy(() -> parser.parse("{rootCause: broken}"))
                    .isInstanceOf(AnalysisException.class);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle unknown extra fields gracefully")
        void shouldIgnoreUnknownFields() {
            String json = """
                    {
                      "rootCause": "Network partition",
                      "confidence": 60,
                      "evidence": [],
                      "recommendations": [],
                      "extraField": "should be ignored",
                      "anotherField": 123
                    }
                    """;

            AiAnalysisResponse result = parser.parse(json);

            assertThat(result.getRootCause()).isEqualTo("Network partition");
            assertThat(result.getConfidence()).isEqualTo(60);
        }

        @Test
        @DisplayName("should handle confidence at boundary values")
        void shouldHandleBoundaryConfidence() {
            String json = """
                    {
                      "rootCause": "Test",
                      "confidence": 0,
                      "evidence": [],
                      "recommendations": []
                    }
                    """;

            AiAnalysisResponse result = parser.parse(json);
            assertThat(result.getConfidence()).isEqualTo(0);
        }
    }
}
