package com.example.incidentinvestigator.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates vector embeddings using Spring AI's EmbeddingModel.
 *
 * <p>Uses the configured Gemini text-embedding-004 model (768 dimensions).
 * Batches chunks for efficiency and handles API communication through
 * Spring AI's abstraction layer.</p>
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int BATCH_SIZE = 20;

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Generates embeddings for a list of text chunks.
     *
     * @param chunks the text chunks to embed
     * @return list of float arrays, one per chunk
     */
    public List<float[]> embedAll(List<String> chunks) {
        log.info("Generating embeddings for {} chunks", chunks.size());
        List<float[]> allEmbeddings = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
            List<String> batch = chunks.subList(i, Math.min(i + BATCH_SIZE, chunks.size()));
            log.debug("Embedding batch {}/{}", (i / BATCH_SIZE) + 1,
                    (int) Math.ceil((double) chunks.size() / BATCH_SIZE));

            EmbeddingResponse response = embeddingModel.call(
                    new org.springframework.ai.embedding.EmbeddingRequest(batch, null)
            );

            response.getResults().forEach(result ->
                    allEmbeddings.add(result.getOutput())
            );
        }

        log.info("Generated {} embeddings (dimension: {})", allEmbeddings.size(),
                allEmbeddings.isEmpty() ? 0 : allEmbeddings.get(0).length);
        return allEmbeddings;
    }

    /**
     * Generates an embedding for a single text query.
     *
     * @param text the text to embed
     * @return the embedding vector
     */
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    /**
     * Converts a float[] embedding to a pgvector-compatible string format.
     * e.g. "[0.1,0.2,0.3]"
     */
    public static String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
