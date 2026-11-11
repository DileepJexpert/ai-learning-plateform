# ADR-002: Hybrid Retrieval with Reciprocal Rank Fusion (RRF) vs. Dense-Only Search

* **Status:** Accepted
* **Date:** 2026-11-11
* **Context:** Regulatory & Compliance Document Search (Blueprint 1)

---

## Context and Problem Statement

Naive RAG systems rely exclusively on dense vector embeddings (e.g. cosine similarity over high-dimensional vectors) for document retrieval. In regulated banking compliance search, dense-only retrieval exhibits critical failure modes:
1. Exact circular numbers (e.g. `RBI/2024-25/112`) or clause identifiers (`Section 4.2(a)`) are frequently scrambled by embedding tokenizers into generic semantic regions.
2. Domain acronyms (e.g. `LCR`, `HQLA`, `NSFR`, `PEPs`) get mapped to distant conceptual neighbors if they were rare in the embedding model's pretraining corpus.

Conversely, pure sparse/BM25 keyword search fails when queries use paraphrased natural language ("capital adequacy buffer requirements" vs "minimum Tier-1 regulatory capital limits").

---

## Decision Drivers

* **Zero-Tolerance for Missed Citations:** Compliance auditors search using both natural language concepts and exact legal identifiers.
* **Score Calibration Incompatibility:** Cosine similarity produces bounded scores $[0, 1]$, whereas BM25 generates unbounded scores $[0, \infty)$ dependent on document frequency. Linear weighted sums ($\alpha \cdot \text{dense} + (1-\alpha) \cdot \text{sparse}$) require delicate and fragile manual tuning.
* **Deterministic Re-ranking:** Need a rank aggregation algorithm that is robust, distribution-agnostic, and parameter-light.

---

## Decision Outcome

**Chosen Solution:** **Hybrid Retrieval with Reciprocal Rank Fusion (RRF)**.

We execute two concurrent retrieval paths in PostgreSQL:
1. **Dense Vector Path:** Retrieves top-$K$ candidates ordered by `embedding <=> query_vector` using an HNSW index.
2. **Sparse Full-Text Path:** Retrieves top-$K$ candidates ordered by `ts_rank_cd(tsv_content, plainto_tsquery('english', query))` using a GIN index.

The results are fused using **Reciprocal Rank Fusion**:
$$RRF(d) = \frac{1}{k + \text{rank}_{\text{dense}}(d)} + \frac{1}{k + \text{rank}_{\text{sparse}}(d)}$$
where $k = 60$ is the standard smoothing constant.

---

## Consequences

* **Positive:**
  * **Superior Precision and Recall:** An exact match on a circular ID guarantees high placement in the sparse ranking, while semantic paraphrases rank high in the dense ranking. If a document appears in both, its fused rank dominates.
  * **No Score Calibration Required:** RRF operates strictly on ordinal ranks (position 1, position 2, ...) rather than raw numerical scores, eliminating threshold tuning headaches.
* **Negative / Mitigation:**
  * Requires generating both an embedding and a full-text query. In PostgreSQL, full-text search executes in sub-millisecond time, adding negligible latency.
