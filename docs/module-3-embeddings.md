# Module 3 — Embeddings & Semantic Search

**Build:** Embed chunks of your own docs using `nomic-embed-text`, store the
vectors in pgvector, and implement cosine-similarity search. Done — see the
`embedding` and `search` packages.

> Run it: `docker compose up -d` + `ollama pull nomic-embed-text`, then
> `./mvnw spring-boot:run` and follow the curl examples below.

---

## The concept in my own words

### What is an embedding?

An embedding is a **function that maps text → a fixed-size vector of floats**
(a list of numbers). For `nomic-embed-text` that's 768 numbers — a point in
768-dimensional space.

The magic: texts with **similar meaning** land **close together** in that space,
even if they use completely different words. "What's the GST on electronics?"
and "tax rate for computers and gadgets" will be nearby vectors, while
"recipe for chocolate cake" will be far away.

This is what makes **semantic** search possible: instead of matching keywords,
you compare the *meaning* of the query to the *meaning* of each stored chunk.

### Vector dimensions and cosine similarity

- **Dimensions:** each model outputs a fixed number of floats (768 for
  nomic-embed-text, 1536 for OpenAI ada-002, etc.). More dimensions =
  richer representation but more storage and slower comparisons.
- **Cosine similarity:** measures the angle between two vectors, ignoring
  magnitude. Range: 1.0 (identical direction = same meaning) to 0.0
  (orthogonal = no relation) to −1.0 (opposite). This is the metric we use
  for search.
  pgvector's `<=>` operator is **cosine distance** (= 1 − similarity);
  `ORDER BY ... ASC` puts the most-similar first.

### Why chunk? And how big?

An embedding model maps one input → one vector. A 50-page BRD must be split
into pieces so each gets its own vector. At search time we retrieve the
most-relevant *chunks*, not the whole document — this is the foundation
Module 4 (RAG) builds on.

**Chunk size tradeoffs:**
- **Smaller chunks** (200–500 chars): more precise retrieval — each hit is
  tightly focused. But less context per hit, and more vectors to store/search.
- **Larger chunks** (1000–2000 chars): more context in each result, fewer
  vectors. But noisier matches — the relevant sentence drowns in surrounding
  padding.

**Overlap:** chunks share some text at the boundary so that a sentence split
across two chunks is fully represented in at least one of them. Typical
overlap: 10–25% of chunk size.

There is no universally "right" size — it depends on the document structure
and what you're retrieving for. The key interview skill is knowing the
tradeoff exists and being able to articulate it.

### pgvector indexes: IVFFlat vs HNSW

pgvector stores vectors in a regular Postgres column (`VECTOR(768)`) and
supports two index types for approximate nearest-neighbour (ANN) search:

| | **IVFFlat** | **HNSW** |
|---|---|---|
| **How it works** | Splits vectors into clusters; at query time, only checks nearby clusters | Builds a multi-layer graph; navigates it greedily |
| **Build time** | Needs a training step after data is loaded | Builds incrementally (no training step) |
| **Query latency** | Fast, but recall depends on `probes` setting | Generally better recall at the same speed |
| **When to use** | Huge datasets (millions of vectors), batch-loaded | Real-time CRUD, moderate scale (our case) |

We use **HNSW** with `vector_cosine_ops` because we do real-time inserts and
don't want a training step. At our scale (hundreds to low thousands of chunks)
a brute-force scan would be fine too — the index is future-proofing.

---

## How it works (the pipeline)

```
              chunk          embed           store
Document ──────────► chunks ──────► vectors ──────► pgvector
  text       (split     text    (Ollama        float[]    (INSERT)
              + overlap)        nomic-embed-text)

                           embed         search
User query ──────────────────────► vector ──────► pgvector
  "What is the GST rate?"   float[]      (ORDER BY <=> LIMIT k)
                                              │
                                              ▼
                                    ranked chunks (top-k)
```

This is exactly half of RAG — the **retrieval** half. Module 4 adds the
**generation** half: stuff the retrieved chunks into a prompt and ask the
chat model to answer using them.

---

## Semantic search vs keyword search

| | **Keyword (LIKE / full-text)** | **Semantic (embedding + cosine)** |
|---|---|---|
| **Matches on** | Exact or stemmed words | Meaning/intent |
| **"GST rate for electronics"** finds... | docs containing "GST", "rate", "electronics" | docs about *tax on electronic goods* (even if phrased differently) |
| **Synonym handling** | Needs a thesaurus / manual rules | Built in — semantically similar words have similar vectors |
| **Speed** | Very fast (B-tree / GIN) | Requires ANN index; slower at large scale |
| **Setup** | No model needed | Needs an embedding model |
| **When it fails** | When the user doesn't know the exact jargon | When similarity is misleading (e.g. antonyms can be close in some models) |

A **hybrid** of both is common in production — keyword search for high-precision
exact matches, semantic search for intent-based discovery, then merge the results.

---

## Interview angles

> **"What's an embedding?"**

A function that maps text to a fixed-size vector of floats so that semantically
similar texts are close in vector space. I used `nomic-embed-text` (768 dims)
running locally on Ollama — the text never leaves the machine.

> **"How does semantic search differ from keyword search?"**

Keyword search matches on words; semantic search matches on *meaning*. "tax rate
for electronic goods" finds documents about *GST on electronics* even if those
exact words don't appear. The price is needing an embedding model and a vector
index (e.g. pgvector with HNSW). Production systems often combine both.

> **"What's chunking and why does it matter?"**

Embedding models map one input → one vector. Big documents must be split into
chunks, each embedded separately. Smaller chunks give more precise retrieval;
larger chunks give more context per hit. Overlapping boundaries ensures sentences
at chunk edges are fully captured. The right size depends on the domain — I
default to 500 chars with 100 overlap and tune from there.

---

### Resume line
> *"Implemented semantic search over internal docs using local embeddings
> (nomic-embed-text + Ollama) stored in pgvector with HNSW indexing and
> cosine-similarity retrieval."*
