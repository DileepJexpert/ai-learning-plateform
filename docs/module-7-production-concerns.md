# Module 7 — Production Concerns: Streaming, Caching, Resilience & Latency Decoupling

**Build:** Production-ready LLM operational infrastructure:
1. **Streaming responses (SSE)** over **Java 21 Virtual Threads (Project Loom)**.
2. **Two-tier response caching**: Tier-1 SHA-256 exact hash + Tier-2 `pgvector` semantic similarity search (HNSW index).
3. **Resilience & Fallback Routing**: automatic failover to lightweight SLMs (`llama3.2:3b`) on timeout or primary model failure.
4. **LLMOps & FinOps Observability**: Time-To-First-Token (TTFT), Time-Per-Output-Token (TPOT), token consumption accounting, and cache hit ratios.

Done — see the `production` package (`ProductionChatController`, `StreamingChatService`, `ResponseCacheService`, `SemanticCacheRepository`, `FallbackRouter`, `ProductionMetricsService`).

> Run it:
> ```bash
> # 1. Regular cached chat (Tier-1 exact hash + Tier-2 pgvector semantic cache)
> curl -s -X POST localhost:8080/api/production/chat \
>   -H 'Content-Type: application/json' \
>   -d '{"prompt":"Explain optimistic vs pessimistic locking in banking."}' | jq
>
> # 2. Real-time token streaming over HTTP SSE (Java 21 Virtual Threads)
> curl -N localhost:8080/api/production/chat/stream?prompt=Explain+optimistic+locking
>
> # 3. Observability & FinOps metrics
> curl -s localhost:8080/api/production/metrics | jq
> ```

---

## The concepts in my own words

### 1. Java 21 Virtual Threads vs. Reactive WebFlux for LLM Streaming

In traditional Java microservices, high-concurrency streaming was often solved using reactive frameworks (Spring WebFlux / Project Reactor). However, reactive programming introduces significant cognitive overhead, complex debugging, fragmented stack traces, and thread-local context loss.

LLM inference is fundamentally **slow, I/O-bound streaming**. Generating 250 tokens at 25 tokens/second takes 10 full seconds. In classic Tomcat thread-per-request architecture with 200 platform OS threads, 200 concurrent users streaming LLM outputs would saturate the entire thread pool, resulting in catastrophic thread exhaustion and 504 Gateway Timeouts.

**The Java 21 Project Loom solution:**
By enabling `spring.threads.virtual.enabled: true` and utilizing `Thread.ofVirtual()`, each incoming SSE stream executes on a cheap user-mode Virtual Thread (mounting and unmounting onto a small carrier thread pool). Millions of virtual threads can wait on network I/O without pinning OS threads. We get the simplicity and debuggability of synchronous imperative code with the massive concurrency of reactive systems.

```
Client ──[SSE GET /stream]──▶ Spring MVC Controller
                                   │
                                   ▼
                       [Java 21 Virtual Thread]
                                   │
               ┌───────────────────┴───────────────────┐
               ▼                                       ▼
    Read NDJSON Chunk from Ollama             Compute TTFT & TPOT
    (unmounts carrier thread while waiting)    (push SSE event to client)
```

---

### 2. Latency Decoupling: TTFT (Time To First Token) vs. TPOT (Time Per Output Token)

In standard REST APIs, we measure latency using P50, P95, and P99 round-trip times. In Generative AI systems, **average latency is a misleading metric**.

A request taking 8 seconds might represent an outstanding user experience or an intolerable outage, depending on the latency breakdown:

1. **TTFT (Time-To-First-Token):**
   * The duration from when the user clicks "Send" until the first word appears on their screen.
   * Measures prompt evaluation time and queue delay.
   * **Perception benchmark:** Human conversational perception requires TTFT &lt; 800ms. If TTFT is fast, users perceive the system as responsive even if total generation takes 15 seconds.
2. **TPOT (Time-Per-Output-Token):**
   * The average time to generate each subsequent token ($ms / token$) or throughput ($tokens / second$).
   * Measures decoder model inference speed.
   * **Reading benchmark:** Human reading speed is approximately 4–5 words/second (~25–30 ms/token). Any TPOT faster than reading speed feels instantaneous to human eyes.

In `StreamingChatService`, we explicitly measure TTFT on the first non-empty token arrival and calculate TPOT upon receiving the terminal `done: true` event:

$$\text{TPOT} = \frac{\text{Total Wall Time} - \text{TTFT}}{\text{Total Response Tokens}}$$

---

### 3. Two-Tier FinOps Caching: Exact SHA-256 vs. High-Dimensional Semantic Caching

In banking and customer-facing enterprise applications, up to 40% of queries are duplicates or semantically identical variations of common questions:
* *"What is the penalty for early withdrawal of a fixed deposit?"*
* *"How much fee is charged if I break my FD early?"*

Sending both to a 7B/14B parameter LLM wastes compute, incurs cloud API costs, and introduces 3,000ms latency.

We built a **two-tier cache pipeline**:

1. **Tier 1 — Exact SHA-256 Hash Cache (In-Memory / Redis):**
   * Deterministic SHA-256 hash over `(model + "::" + normalized_prompt)`.
   * **Latency:** &lt; 1 ms.
   * **Cost:** 0 tokens.
   * Catches exact duplicates instantly without computing embeddings.
2. **Tier 2 — Semantic Cache with pgvector (`1 - (embedding <=> query_vec) >= threshold`):**
   * When Tier 1 misses, we compute the query embedding using `nomic-embed-text` (a fast 15ms vector computation).
   * We search the `semantic_cache` table using pgvector's cosine distance (`<=>`) backed by an HNSW index.
   * If similarity $\ge 0.90$, we return the previously generated answer with metadata (`cached: true, cacheType: "SEMANTIC", similarity: 0.94`).
   * **Latency:** Drops from 3,500ms down to ~20ms.
   * **Cost:** Saves 100% of generation tokens.

```
Incoming User Query
         │
         ▼
[Compute SHA-256] ──HIT──▶ Return Cached Response (<1ms)
         │
       MISS
         ▼
[Embed via nomic-embed-text]
         │
         ▼
[Query pgvector semantic_cache] ──HIT (>= 0.90)──▶ Return Semantic Match (~20ms)
         │
       MISS
         ▼
[Execute LLM Inference] ──▶ Save to Tier 1 & Tier 2 ──▶ Return Response
```

---

### 4. Dynamic Fallback Routing & SLA Protection

In production banking platforms governed by SLAs, an LLM service cannot return raw 500 errors or unhandled timeouts when a primary GPU host suffers degradation.

`FallbackRouter` implements an automated multi-tier failover policy:
* **Primary Tier:** High-capability model (`qwen2.5-coder:7b` or frontier cloud model).
* **Secondary Fallback Tier:** Fast, compact SLM (`llama3.2:3b` or local edge model).
* **Circuit Breaker / Failover:** If the primary model times out or encounters transport errors, the router catches the exception, increments the `fallbackCount` metric, and automatically reroutes the prompt to the secondary tier.
* The response explicitly includes `fallbackUsed: true` and the `primaryFailureReason` for post-incident auditability.

---

## Architectural Interview Prep

### Q1: "Why choose Java 21 Virtual Threads over WebFlux for streaming LLM responses?"
> **Answer:** "LLM streaming is long-lived I/O: generating 300 tokens takes 6–10 seconds of open HTTP connection. In traditional thread-per-request models, this exhausts thread pools rapidly. Spring WebFlux can handle this reactively, but introduces steep complexity—loss of standard thread-local security context, difficult stack traces, and complete codebase refactoring. 
> With Java 21 Project Loom Virtual Threads (`spring.threads.virtual.enabled: true`), each SSE streaming connection runs on a lightweight user-space virtual thread that unmounts from the carrier OS thread whenever it blocks on the LLM's next chunk. We achieve the scalability of reactive architectures while retaining simple, clean, imperative Java code."

### Q2: "How does semantic caching work in an enterprise AI platform, and what are its trade-offs?"
> **Answer:** "Exact key-value caching (Redis) fails on natural language because users rarely type the exact same string. Semantic caching maps the user prompt into a high-dimensional vector space using a fast embedding model, then queries a vector database (`pgvector`) using cosine similarity. If the cosine similarity exceeds a safe threshold (typically 0.90 to 0.93 for banking FAQ/retrieval), the cached response is served.
> **Trade-offs:** 
> 1. *Precision vs. Recall:* Too low a threshold (e.g. 0.80) causes false positive cache hits, serving irrelevant answers. Too high (e.g. 0.98) causes cache misses.
> 2. *Embedding overhead:* Generating the query embedding takes ~15–20ms. If the cache miss rate is high, that adds 20ms of latency to the primary LLM call. That is why we prepend an exact SHA-256 hash cache as Tier 1."

### Q3: "What metrics do you monitor for production LLM services?"
> **Answer:** "Traditional latency percentiles (P95/P99) do not tell the whole story for LLMs. In production, we decouple latency into:
> 1. **TTFT (Time-To-First-Token):** Measures time to initial response delivery; dictates perceived responsiveness (&lt;800ms target).
> 2. **TPOT (Time-Per-Output-Token):** Measures decoder generation speed; must exceed human reading speed (~25–30 ms/token).
> 3. **Token Accounting:** Prompt tokens vs. completion tokens to project FinOps burn rate and detect prompt drift.
> 4. **Cache Hit Ratio (Exact vs. Semantic):** Quantifies computational and cost savings.
> 5. **Failover Rate:** Percentage of requests rerouted to fallback SLMs due to primary model degradation."
