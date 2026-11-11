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

-- Module 7: pgvector schema for semantic response caching.
-- Reduces latency and token consumption for semantically similar user prompts.
CREATE TABLE IF NOT EXISTS semantic_cache (
    id              BIGSERIAL PRIMARY KEY,
    query_text      TEXT NOT NULL,
    response_text   TEXT NOT NULL,
    model           TEXT NOT NULL,
    embedding       VECTOR(768),         -- 768 dimensions for nomic-embed-text
    prompt_tokens   INT,
    response_tokens INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_semantic_cache_embedding_cosine
    ON semantic_cache
    USING hnsw (embedding vector_cosine_ops);

-- ===========================================================================
-- Blueprint 1: Compliance & Regulatory Documents with Hybrid Search
-- Combines PostgreSQL Full-Text Search (sparse/BM25) with pgvector (dense)
-- ===========================================================================
CREATE TABLE IF NOT EXISTS compliance_clauses (
    id              BIGSERIAL PRIMARY KEY,
    circular_id     TEXT NOT NULL,          -- e.g. "RBI/2024-25/112"
    circular_title  TEXT NOT NULL,          -- e.g. "Master Direction - Know Your Customer (KYC)"
    authority       TEXT NOT NULL,          -- e.g. "Reserve Bank of India"
    effective_date  TEXT NOT NULL,          -- e.g. "2024-04-01"
    clause_ref      TEXT NOT NULL,          -- e.g. "Section 4.2(a)"
    section_title   TEXT NOT NULL,          -- e.g. "Periodic Updation of KYC Data"
    content         TEXT NOT NULL,          -- sanitized legal/regulatory clause text
    tsv_content     TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(circular_id, '') || ' ' || coalesce(clause_ref, '') || ' ' || coalesce(section_title, '') || ' ' || coalesce(content, ''))
    ) STORED,
    embedding       VECTOR(768),            -- 768 dimensions for nomic-embed-text
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- GIN index for sparse full-text search
CREATE INDEX IF NOT EXISTS idx_compliance_clauses_tsv
    ON compliance_clauses
    USING gin(tsv_content);

-- HNSW index for dense vector cosine distance
CREATE INDEX IF NOT EXISTS idx_compliance_clauses_embedding_cosine
    ON compliance_clauses
    USING hnsw (embedding vector_cosine_ops);


