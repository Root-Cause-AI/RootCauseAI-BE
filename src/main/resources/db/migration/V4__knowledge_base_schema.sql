-- Flyway migration V4: Knowledge Base schema for Incident Intelligence RAG
-- Adds pgvector extension, knowledge document/chunk tables, and RAG context to reports.

-- 1. Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Knowledge Documents — stores uploaded runbooks, past incidents, known fixes, architecture docs
CREATE TABLE knowledge_documents (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(500)    NOT NULL,
    content         TEXT            NOT NULL,
    document_type   VARCHAR(50)     NOT NULL,
    file_name       VARCHAR(500),
    file_type       VARCHAR(20),
    metadata        JSONB           DEFAULT '{}',
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_documents_type ON knowledge_documents (document_type);
CREATE INDEX idx_knowledge_documents_created ON knowledge_documents (created_at DESC);
CREATE INDEX idx_knowledge_documents_metadata ON knowledge_documents USING GIN (metadata);

-- 3. Knowledge Chunks — stores embedded text chunks for vector similarity search
CREATE TABLE knowledge_chunks (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID            NOT NULL,
    chunk_index     INTEGER         NOT NULL,
    content         TEXT            NOT NULL,
    embedding       vector(768),
    token_count     INTEGER,
    search_vector   tsvector,
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_chunks_document
        FOREIGN KEY (document_id)
        REFERENCES knowledge_documents (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_chunks_document ON knowledge_chunks (document_id);

-- HNSW index for fast approximate nearest neighbor search on embeddings
CREATE INDEX idx_knowledge_chunks_embedding ON knowledge_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- GIN index for full-text keyword search
CREATE INDEX idx_knowledge_chunks_search ON knowledge_chunks USING GIN (search_vector);

-- 4. Add RAG context column to existing reports table
ALTER TABLE reports
    ADD COLUMN rag_context TEXT;
