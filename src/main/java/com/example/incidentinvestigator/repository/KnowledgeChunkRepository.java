package com.example.incidentinvestigator.repository;

import com.example.incidentinvestigator.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link KnowledgeChunk} entities.
 *
 * <p>Contains native SQL queries for pgvector similarity search
 * and PostgreSQL full-text search operations.</p>
 */
@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {

    /**
     * Finds the most similar chunks by cosine distance using pgvector.
     * Lower distance = more similar. Returns chunks with their distance score.
     */
    @Query(value = """
            SELECT c.id, c.document_id, c.chunk_index, c.content, c.token_count, c.created_at,
                   (c.embedding <=> cast(:embedding as vector)) as distance
            FROM knowledge_chunks c
            WHERE c.embedding IS NOT NULL
            ORDER BY c.embedding <=> cast(:embedding as vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findSimilarByEmbedding(@Param("embedding") String embedding, @Param("limit") int limit);

    /**
     * Full-text keyword search using PostgreSQL tsvector/tsquery.
     */
    @Query(value = """
            SELECT c.id, c.document_id, c.chunk_index, c.content, c.token_count, c.created_at,
                   ts_rank(c.search_vector, plainto_tsquery('english', :query)) as rank
            FROM knowledge_chunks c
            WHERE c.search_vector @@ plainto_tsquery('english', :query)
            ORDER BY rank DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findByKeyword(@Param("query") String query, @Param("limit") int limit);

    long countByDocumentId(UUID documentId);

    @Modifying
    @Query("DELETE FROM KnowledgeChunk c WHERE c.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    /**
     * Inserts a chunk with embedding and search_vector using native SQL.
     * This bypasses JPA's lack of pgvector type support.
     */
    @Modifying
    @Query(value = """
            INSERT INTO knowledge_chunks (id, document_id, chunk_index, content, embedding, token_count, search_vector, created_at)
            VALUES (:id, :documentId, :chunkIndex, :content, cast(:embedding as vector), :tokenCount, to_tsvector('english', :content), now())
            """, nativeQuery = true)
    void insertWithEmbedding(
            @Param("id") UUID id,
            @Param("documentId") UUID documentId,
            @Param("chunkIndex") int chunkIndex,
            @Param("content") String content,
            @Param("embedding") String embedding,
            @Param("tokenCount") int tokenCount
    );
}
