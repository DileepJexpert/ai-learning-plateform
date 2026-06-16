-- Module 3: pgvector schema for document chunk embeddings.
--
-- pgvector adds the VECTOR type to Postgres, which stores a fixed-dimension
-- float array and supports efficient similarity-search indexes (IVFFlat, HNSW).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document_chunks (
    id          BIGSERIAL PRIMARY KEY,
    doc_name    TEXT    NOT NULL,     -- which document this chunk came from (e.g. "gst-rules.txt")
    chunk_index INT     NOT NULL,     -- position within the document (0-based)
    content     TEXT    NOT NULL,     -- the chunk text
    embedding   VECTOR(768),         -- 768 dimensions for nomic-embed-text
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Cosine-distance index for semantic search (HNSW is better than IVFFlat for
-- real-time queries at moderate scale — no training step needed).
-- lists/m/ef_construction are left at pgvector defaults; tune when data > 100k rows.
CREATE INDEX IF NOT EXISTS idx_chunks_embedding_cosine
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops);
