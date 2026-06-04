package com.example.incidentinvestigator.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits document text into overlapping chunks for embedding.
 *
 * <p>Strategy: paragraph-aware splitting. The text is split on double-newline
 * boundaries first, then paragraphs are merged into chunks of approximately
 * the target size, with configurable overlap to preserve context across
 * chunk boundaries.</p>
 */
@Component
public class TextChunker {

    private static final int TARGET_CHUNK_SIZE = 2000; // ~500 tokens
    private static final int OVERLAP_SIZE = 400;       // ~100 tokens

    /**
     * Splits text into overlapping chunks.
     *
     * @param text the full document text
     * @return list of text chunks
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");

        // If the text is short enough, return as a single chunk
        if (normalized.length() <= TARGET_CHUNK_SIZE) {
            return List.of(normalized.trim());
        }

        // Split into paragraphs
        String[] paragraphs = normalized.split("\n\n+");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;

            // If adding this paragraph would exceed the target, finalize the current chunk
            if (current.length() + trimmed.length() + 2 > TARGET_CHUNK_SIZE && current.length() > 0) {
                chunks.add(current.toString().trim());

                // Start next chunk with overlap from the end of the current chunk
                String currentText = current.toString();
                int overlapStart = Math.max(0, currentText.length() - OVERLAP_SIZE);
                current = new StringBuilder(currentText.substring(overlapStart).trim());
                current.append("\n\n");
            }

            current.append(trimmed).append("\n\n");
        }

        // Add the final chunk
        if (current.length() > 0) {
            String lastChunk = current.toString().trim();
            if (!lastChunk.isEmpty()) {
                chunks.add(lastChunk);
            }
        }

        return chunks;
    }

    /**
     * Estimates token count for a text string.
     * Rough heuristic: ~4 characters per token for English text.
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.length() / 4;
    }
}
