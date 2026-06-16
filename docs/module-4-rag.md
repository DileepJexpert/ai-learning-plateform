# Module 4 — RAG (Retrieval-Augmented Generation) ⭐

**Build:** The full pipeline —
`chunk → embed → store → retrieve top-k → re-rank → stuff into prompt → answer with citations`.
Done — see the `rag` package (built on Module 3's retrieval).

> This is **the most-asked applied-AI interview topic.** Be able to whiteboard it cold.

> Run it: ingest docs (Module 3 `POST /api/embeddings/ingest`), then
> `POST /api/rag/ask` with a question.

---

## The concept in my own words

A base LLM only knows what was in its training data. It can't answer questions
about *your* documents, and when it doesn't know, it often **hallucinates** a
confident-sounding wrong answer.

**RAG fixes this by changing the question** from "What's the answer?" (asking the
model to recall) to "Here are the relevant facts — answer using only these"
(asking the model to *read and synthesise*). You retrieve relevant text from your
own corpus and put it in the prompt as grounding context.

The pipeline, end to end:

```
INGEST (Module 3, offline):
  document → chunk → embed → store vectors in pgvector

QUERY (Module 4, per request):
  question → embed → retrieve top candidates (vector search)
           → RE-RANK (MMR: relevance + diversity)
           → BUDGET (drop chunks that don't fit the context window)
           → STUFF into a grounded prompt with numbered sources
           → LLM answers using only that context, citing [1], [2]...
           → return answer + the source chunks (attribution)
```

The first three boxes of the query path are Module 3. Module 4 adds re-ranking,
budgeting, grounding, generation, and citations.

### Retrieval + grounding = the core anti-hallucination technique

Two things reduce hallucination here:
1. **Retrieval** puts the actual facts in front of the model, so it doesn't have
   to recall them (and can't recall what it never learned — like your internal docs).
2. **Grounding instructions** in the system prompt force the model to answer
   *only* from those facts and to say "I don't know" when they're insufficient —
   instead of filling the gap with a plausible guess.

Neither alone is enough. Retrieval without grounding → the model still mixes in
its own (possibly wrong) prior knowledge. Grounding without retrieval → nothing
to ground on. Together they're powerful.

### Why not just put everything in the prompt?

Two hard limits:
- **Context window.** Models have a finite input size (e.g. 8k tokens here). A
  50-page manual simply doesn't fit. Even when a big document *does* fit, it's
  wasteful and slow.
- **Signal-to-noise / "lost in the middle."** Models attend worse to information
  buried in a huge context. Feeding 50 pages to answer one question *lowers*
  accuracy versus feeding the 4 most relevant paragraphs. Retrieval is a
  relevance filter, not just a size workaround.

So we **budget**: retrieve a candidate pool, re-rank, and stuff only the top few
chunks that fit a character/token budget. This project caps context at
`rag.max-context-chars` and keeps the best chunks until that's exhausted.

### Re-ranking, and why MMR

Pure vector search returns the *most similar* chunks — which are frequently
**near-duplicates** of each other. If your top-5 are five paraphrases of the same
sentence, you've wasted the context window and starved the model of other
relevant facts.

This project re-ranks with **MMR (Maximal Marginal Relevance)**: pick chunks that
are relevant to the query *and* different from ones already picked.

```
score(d) = λ · relevance(d) − (1 − λ) · max_similarity(d, alreadyPicked)
```

- `λ = 1.0` → pure relevance (no diversity) — same as plain top-k.
- `λ = 0.0` → pure diversity (ignores relevance).
- We default to `λ = 0.6`.

MMR is a great fit for a local-only stack because it needs **no extra model** — it
reuses the embeddings already in pgvector. (The heavier alternative is a
**cross-encoder re-ranker**: a model that scores (query, chunk) pairs directly.
More accurate, but it's another model to run.)

### Citation / attribution

Every chunk we stuff is numbered `[1] (doc-name) ...`, and the model is told to
cite with those numbers. We return the numbered sources alongside the answer, so
each claim is **traceable to an exact document chunk**. This turns a black-box
answer into an auditable one — essential for regulated/fintech use.

---

## Tuning levers (all in `application.yml` under `rag.*`)

| Lever | Default | Effect |
|-------|---------|--------|
| `top-k` | 4 | chunks actually put in the prompt — more context vs. more noise |
| `fetch-k` | 20 | candidate pool before re-ranking — bigger = more for MMR to choose from |
| `rerank` | true | MMR on/off |
| `mmr-lambda` | 0.6 | relevance↔diversity balance |
| `max-context-chars` | 6000 | context-window budget |
| `temperature` | 0.1 | low = faithful to context, not creative |
| `chunk-size` / `chunk-overlap` | 500 / 100 | (Module 3) the granularity retrieval operates on |

Chunk size and `top-k` interact: small chunks + larger `top-k` gives precise,
well-cited answers; large chunks + small `top-k` gives more context per hit but
coarser citations.

---

## Interview angles

> **"Walk me through how RAG works."**

Offline, I chunk my documents, embed each chunk, and store the vectors
(pgvector). At query time I embed the question, vector-search for the most
similar chunks, re-rank them (MMR, for relevance + diversity), drop whatever
won't fit the context budget, and stuff the rest into a prompt as numbered
sources. The system prompt tells the model to answer *only* from those sources,
cite them, and say "I don't know" otherwise. I return the answer plus the source
chunks for attribution.

> **"How do you reduce hallucinations?"**

Retrieval + grounding. Retrieval puts the real facts in the prompt so the model
reads instead of recalls; grounding instructions force it to use only those facts
and to refuse when they're insufficient. I keep temperature low, and I make
answers auditable with citations so wrong answers are catchable. Validation/eval
(Module 6) closes the loop.

> **"Why not just put everything in the prompt?"**

It often doesn't fit the context window, it's slow and costly, and accuracy
actually drops when relevant facts are buried in a huge context ("lost in the
middle"). Retrieval is a relevance filter that gives the model a small, focused,
high-signal context.

> **"How would you make RAG answers better?"**

Tune chunk size/overlap to the document structure; over-fetch then re-rank
(MMR or a cross-encoder); hybrid search (keyword + vector) for exact-term recall;
add citations + an eval set to measure faithfulness; and budget context
deliberately rather than dumping everything.

---

### Resume line
> *"Built an offline RAG pipeline over compliance documents (Ollama + pgvector):
> embed → vector-retrieve → MMR re-rank → context-budgeted, grounded generation
> with source citations — for regulated-environment use."*
