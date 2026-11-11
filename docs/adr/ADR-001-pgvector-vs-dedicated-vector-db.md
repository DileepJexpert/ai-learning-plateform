# ADR-001: Selection of pgvector over Dedicated Vector Databases (Pinecone / Qdrant / Milvus)

* **Status:** Accepted
* **Date:** 2026-11-11
* **Context:** Enterprise Banking & Regulated Fintech AI Platform

---

## Context and Problem Statement

When building generative AI applications and semantic retrieval engines for Tier-1 banks, a key architectural decision is where to store and index high-dimensional embedding vectors. 

The primary options considered were:
1. **Dedicated Managed Vector Databases:** Pinecone, Qdrant Cloud, Zilliz/Milvus.
2. **Relational Extension within Existing Enterprise Footprint:** PostgreSQL with the `pgvector` extension.

The decision must balance search latency, transactional integrity (ACID), zero-trust security boundaries, regulatory compliance, and operational overhead.

---

## Decision Drivers

* **Data Residency & Boundary Isolation:** Regulated banking documents cannot traverse external public third-party SaaS APIs without explicit regulatory approval.
* **ACID Transactions & Unified Metadata:** Relational document metadata (circular IDs, authorizer signatures, revision history, tenant IDs) must remain strictly consistent with embedding vectors.
* **Operational Complexity (TCO):** Introducing a new standalone database cluster requires new operational runbooks, backup/restore procedures, disaster recovery (DR) testing, and security auditing.
* **Hybrid Search Capabilities:** Need to combine relational SQL filtering and Full-Text Search (BM25) with vector similarity without distributed two-phase commit overhead.

---

## Considered Options

### Option 1: Standalone Vector Database (Pinecone / Qdrant)
* **Pros:** Specialized vector clustering algorithms, high throughput for billion-scale vector indexes.
* **Cons:** Dual-write problem (distributed transactions between relational store and vector DB), separate authentication and access controls, egress outside enterprise VPC (for cloud SaaS), high licensing and operational costs.

### Option 2: PostgreSQL with `pgvector` (Selected)
* **Pros:**
  * Runs natively within existing hardened PostgreSQL clusters inside private enterprise subnets.
  * Native ACID transactions: inserting a circular clause and its vector is atomic.
  * HNSW (Hierarchical Navigable Small World) indexing provides sub-10ms nearest-neighbor search for collections up to millions of vectors.
  * Unified query engine: relational filters (`WHERE circular_id = ...`), Full-Text Search (`tsvector`), and cosine distance (`<=>`) execute in a single SQL query plan.
* **Cons:**
  * At massive scale (>100M+ vectors with concurrent high-write loads), dedicated vector databases scale horizontally more readily.

---

## Decision Outcome

**Chosen Option:** **PostgreSQL with `pgvector`**.

For enterprise banking regulatory and compliance corpora (tens of thousands of circulars, millions of clauses), `pgvector` with HNSW indexing provides single-digit millisecond latency while completely eliminating the dual-write consistency hazard and satisfying strict data boundary regulations.

---

## Consequences

* **Positive:**
  * Zero external data egress; runs entirely within bank-managed VPCs or on-prem Kubernetes clusters.
  * Seamless relational joins between compliance audit logs and vector chunks.
  * Backup, replication, and disaster recovery inherit existing PostgreSQL tooling (WAL archiving, pg_dump, Patroni).
* **Negative / Mitigation:**
  * Memory footprint: HNSW indexes reside in RAM. Mitigated by sizing PostgreSQL `shared_buffers` and `maintenance_work_mem` appropriately.
