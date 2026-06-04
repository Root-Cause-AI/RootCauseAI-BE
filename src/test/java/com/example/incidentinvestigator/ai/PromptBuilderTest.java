package com.example.incidentinvestigator.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;

@DisplayName("PromptBuilder Tests")
class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    @DisplayName("should build prompt with logs and deployment info")
    void shouldBuildPromptWithAllData() {
        String prompt = promptBuilder.buildPrompt(
                "ERROR: Connection timeout at 14:32:00",
                "version: 2.1.0, deployed at: 14:30:00",
                List.of()
        );

        assertThat(prompt)
                .contains("senior Site Reliability Engineer")
                .contains("ERROR: Connection timeout at 14:32:00")
                .contains("version: 2.1.0, deployed at: 14:30:00")
                .contains("rootCause")
                .contains("confidence")
                .contains("evidence")
                .contains("recommendations");
    }

    @Test
    @DisplayName("should handle null logs gracefully")
    void shouldHandleNullLogs() {
        String prompt = promptBuilder.buildPrompt(null, "version: 2.1.0", List.of());

        assertThat(prompt)
                .contains("No logs provided.")
                .contains("version: 2.1.0");
    }

    @Test
    @DisplayName("should handle null deployment info gracefully")
    void shouldHandleNullDeploymentInfo() {
        String prompt = promptBuilder.buildPrompt("ERROR: timeout", null, List.of());

        assertThat(prompt)
                .contains("ERROR: timeout")
                .contains("No deployment information provided.");
    }

    @Test
    @DisplayName("should handle blank inputs gracefully")
    void shouldHandleBlankInputs() {
        String prompt = promptBuilder.buildPrompt("   ", "   ", List.of());

        assertThat(prompt)
                .contains("No logs provided.")
                .contains("No deployment information provided.");
    }

    @Test
    @DisplayName("should handle both null inputs")
    void shouldHandleBothNull() {
        String prompt = promptBuilder.buildPrompt(null, null, List.of());

        assertThat(prompt)
                .contains("No logs provided.")
                .contains("No deployment information provided.")
                .contains("Return ONLY valid JSON");
    }

    @Test
    @DisplayName("should trim input data")
    void shouldTrimInputs() {
        String prompt = promptBuilder.buildPrompt(
                "  ERROR: timeout  ",
                "  version: 2.1.0  ",
                List.of()
        );

        assertThat(prompt)
                .contains("ERROR: timeout")
                .contains("version: 2.1.0")
                .doesNotContain("  ERROR")
                .doesNotContain("timeout  ");
    }

    @Test
    @DisplayName("should include RAG context when provided")
    void shouldIncludeRagContext() {
        java.util.UUID chunkId = java.util.UUID.randomUUID();
        java.util.UUID docId = java.util.UUID.randomUUID();
        com.example.incidentinvestigator.knowledge.RetrievalResult ragResult = new com.example.incidentinvestigator.knowledge.RetrievalResult(
                chunkId, docId, "DB Runbook", "RUNBOOK", "Increase pool size to 50", 0.95
        );

        String prompt = promptBuilder.buildPrompt(
                "ERROR", "Info", List.of(), List.of(ragResult)
        );

        assertThat(prompt)
                .contains("KNOWLEDGE BASE CONTEXT")
                .contains("DB Runbook")
                .contains("RUNBOOK")
                .contains("Increase pool size to 50");
    }
}
