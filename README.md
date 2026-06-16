# AI Learning Platform — Applied AI for Backend Engineers

A runnable, interview-ready reference project for orchestrating **local LLMs (Ollama)**
from **Spring Boot**. You're not training models here — you're *orchestrating* them.

This repo follows a module-by-module learning plan. What's built **right now**:

| Module | Topic | Status |
|--------|-------|--------|
| **0** | Baseline — call a local model over HTTP | ✅ built |
| **1** | Prompt engineering & **structured output** (hardened invoice extractor) | ✅ built |
| **2** | **Vision / multimodal extraction** (invoice photo → same schema) | ✅ built |
| **3** | **Embeddings & semantic search** (pgvector + nomic-embed-text) | ✅ built |
| 4 | RAG (retrieval-augmented generation) | ⏳ next |
| 5–8 | Tools/agents, eval, production, system design | 🗺️ planned |

> **The headline feature:** an invoice → JSON extractor that returns **valid,
> schema-correct JSON every time** — strict schema + few-shot + null handling +
> auto-retry + a validation guardrail that rejects bad output before it could
> reach a database.

---

## Prerequisites

1. **Java 21+** (`java -version`)
2. **[Ollama](https://ollama.com)** running locally:
   ```bash
   ollama serve                 # starts the server on http://localhost:11434
   ollama pull qwen2.5-coder:7b # the default model used here
   ollama pull llama3.2-vision  # Module 2: reading invoice photos/scans
   ollama pull nomic-embed-text # Module 3: embeddings & semantic search
   # optional alternates:
   ollama pull qwen2.5-coder:14b   # max quality
   ollama pull llama3.2:3b         # fast smoke tests
   ```
3. **Docker** (for Module 3+ — pgvector):
   ```bash
   docker compose up -d    # starts Postgres + pgvector on localhost:5432
   ```
   Nothing leaves your machine — that's the whole point of local inference
   (privacy / cost / control; great for regulated or air-gapped environments).

No Maven install needed — use the bundled wrapper (`./mvnw`).

---

## Quick start

```bash
# 1. start pgvector (needed for Module 3+ tests and the app itself)
docker compose up -d

# 2. run the tests (Ollama is mocked; pgvector runs in Docker)
./mvnw test

# 3. start the service (needs both Ollama and pgvector running)
./mvnw spring-boot:run
```

Then, in another terminal:

```bash
# Module 0 — basic chat round-trip
curl -s localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"In one sentence, what is an embedding?"}' | jq

# Module 1 — structured extraction from a bundled sample (zero setup)
curl -s -X POST localhost:8080/api/invoices/extract-sample/clean | jq
curl -s -X POST localhost:8080/api/invoices/extract-sample/missing-fields | jq

# Module 1 — extract your own invoice text
curl -s localhost:8080/api/invoices/extract \
  -H 'Content-Type: application/json' \
  -d '{"text":"Invoice No: INV-9\nFrom: Foo Ltd  GSTIN: 27AABCF1111A1Z5\nWidget x2  Rate 50.00  Amount 100.00\nGST 18% 18.00\nTotal 118.00\nCurrency INR"}' | jq

# Module 2 — extract from a photo/scan (needs: ollama pull llama3.2-vision)
curl -s -F file=@/path/to/invoice.jpg localhost:8080/api/invoices/extract-image | jq

# Module 3 — ingest a document for semantic search (needs: nomic-embed-text + pgvector)
curl -s localhost:8080/api/embeddings/ingest \
  -H 'Content-Type: application/json' \
  -d '{"docName":"gst-notes","text":"GST rates: 5% essentials, 12% processed food, 18% services and electronics, 28% luxury goods."}' | jq

# Module 3 — semantic search across all ingested documents
curl -s localhost:8080/api/embeddings/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"What is the GST rate for electronic goods?","k":3}' | jq
```

`requests.http` has the same calls for the IntelliJ/VS Code REST client.

---

## What you get back

`extract-sample/clean` returns the parsed invoice **plus observability metadata**:

```json
{
  "invoice": {
    "invoiceNumber": "SE/2025/1187",
    "invoiceDate": "28/05/2025",
    "vendorName": "Sunrise Electricals Pvt Ltd",
    "vendorGstin": "27AADCS9012F1Z7",
    "buyerName": "Northwind Retail Ltd",
    "buyerGstin": "29AAFCN3344K1Z9",
    "currency": "INR",
    "lineItems": [
      {"description":"LED Panel 18W","hsnCode":"9405","quantity":50,"unitPrice":220.00,"taxRate":null,"lineTotal":11000.00}
      // ...
    ],
    "subtotal": 25550.00,
    "taxAmount": 4599.00,
    "totalAmount": 30149.00
  },
  "model": "qwen2.5-coder:7b",
  "attempts": 1,          // 2 means the auto-retry kicked in
  "durationMs": 1843,
  "promptTokens": 712,
  "responseTokens": 240
}
```

(Exact values vary by model — local LLMs aren't perfectly consistent, which is
*exactly* why the hardening below matters.)

---

## How the Module 1 hardening works

This is the part worth being able to explain in an interview. The reliability
recipe lives in [`InvoiceExtractionService`](src/main/java/com/dileep/ailearning/invoice/InvoiceExtractionService.java):

1. **System prompt = the contract.** A strict JSON schema, hard rules, and **two
   few-shot examples** (one deliberately showing missing fields → `null`). See
   [`InvoicePromptFactory`](src/main/java/com/dileep/ailearning/invoice/InvoicePromptFactory.java).
   The user prompt is just the raw invoice — *contract vs data*.
2. **`format: "json"` + `temperature: 0` + fixed `seed`.** Ollama constrains
   decoding to valid JSON; temperature 0 and a seed make it deterministic and
   reproducible. (Weak local models still need the schema *spelled out* in the
   prompt — `format: json` guarantees *valid* JSON, not the *right shape*.)
3. **Parse** the reply into a typed `Invoice` record (money is `BigDecimal`,
   never `double`). A [`JsonSanitizer`](src/main/java/com/dileep/ailearning/common/JsonSanitizer.java)
   safety-net strips stray code fences/prose.
4. **Validate** with Jakarta Bean Validation — the **guardrail**. Missing
   `invoiceNumber`? Zero line items? Rejected.
5. **Auto-retry once.** On invalid/unschema'd output we hand the model *its own
   bad answer + the exact reason it failed* and ask again. Still bad → we throw
   a clean **422** and **never persist garbage**.

```
raw text ──> [system prompt + few-shot] ──> Ollama (format:json, temp 0)
                                               │
                                          parse JSON ──fail──┐
                                               │             │ append bad output
                                          validate ──fail────┤ + reason, retry once
                                               │ ok          │
                                          ExtractionResult   └─► 422 (rejected)
```

> **Module 2 (vision) reuses every box above.** An invoice *photo* enters as a
> base64-image message to a vision model (`llama3.2-vision`); from "parse JSON"
> onward the pipeline — schema, validation, auto-retry, reject — is identical.
> See [`docs/module-2-vision.md`](docs/module-2-vision.md).

---

## Project layout

```
src/main/java/com/dileep/ailearning/
├─ AiLearningApplication.java        # entry point
├─ config/                           # typed config (OllamaProperties, ...) + RestClient bean
├─ ollama/                           # MODULE 0: the only code that talks to Ollama
│  ├─ OllamaClient.java              #   chat() + embed() — logs token usage
│  └─ dto/                           #   wire format (ChatRequest/Response, EmbedRequest/Response, ...)
├─ chat/ChatController.java          # MODULE 0: POST /api/chat demo endpoint
├─ invoice/                          # MODULE 1 & 2: structured extraction
│  ├─ InvoicePromptFactory.java      #   the schema + few-shot contract  ← the "feature"
│  ├─ InvoiceExtractionService.java  #   extract(text) + extractFromImage(bytes), shared retry loop
│  ├─ InvoiceController.java         #   POST /api/invoices/extract[-sample] (text) + /extract-image (photo)
│  └─ model/{Invoice,LineItem}.java  #   target schema + validation guardrails
├─ embedding/                        # MODULE 3: embeddings & vector storage
│  ├─ TextChunker.java               #   split documents into overlapping chunks
│  ├─ EmbeddingService.java          #   chunk → embed (via Ollama) → store in pgvector
│  ├─ ChunkRepository.java           #   pgvector SQL: insert + cosine-similarity search
│  └─ DocumentChunk.java             #   stored chunk record
├─ search/SemanticSearchController   # MODULE 3: POST /api/embeddings/{ingest,search}
└─ common/                           # JsonSanitizer, GlobalExceptionHandler (RFC-7807)
```

`docs/` has a one-page concept note per module ([Module 0](docs/module-0-baseline.md),
[Module 1](docs/module-1-structured-output.md), [Module 2](docs/module-2-vision.md),
[Module 3](docs/module-3-embeddings.md)) — written as interview prep.

---

## Configuration

All in [`application.yml`](src/main/resources/application.yml); override via env vars or flags:

| Key | Default | Meaning |
|-----|---------|---------|
| `ollama.base-url` | `http://localhost:11434` | where Ollama listens |
| `ollama.model` | `qwen2.5-coder:7b` | default chat model |
| `invoice.extraction.model` | `qwen2.5-coder:7b` | text model for extraction (escalate to `:14b` for hard docs) |
| `invoice.extraction.vision-model` | `llama3.2-vision` | multimodal model for image/photo extraction (Module 2) |
| `invoice.extraction.temperature` | `0.0` | 0 = deterministic; never raise for extraction |
| `invoice.extraction.seed` | `42` | fixed seed → reproducible runs |
| `invoice.extraction.max-retries` | `1` | "auto-retry once" |
| `embedding.model` | `nomic-embed-text` | embedding model (Module 3) |
| `embedding.dimensions` | `768` | must match the model's output dimension |
| `embedding.chunk-size` | `500` | target chunk size in characters |
| `embedding.chunk-overlap` | `100` | overlap between consecutive chunks |

```bash
# e.g. use the bigger model just for extraction:
./mvnw spring-boot:run -Dspring-boot.run.arguments=--invoice.extraction.model=qwen2.5-coder:14b
```

---

## Interview ammo (resume lines)

- *"Built a local-LLM invoice-extraction service (Spring Boot + Ollama) with
  schema-enforced JSON output, few-shot prompting, and auto-retry on invalid output."*
- *"Added a Bean-Validation guardrail that rejects malformed model output before
  persistence, returning RFC-7807 problem responses."*
- *"Extended it to multimodal input — base64 image → vision model — reusing the
  same schema-validated domain object and retry pipeline as the text path."*
- *"Implemented semantic search over internal docs using local embeddings
  (nomic-embed-text) stored in pgvector with HNSW indexing and cosine-similarity
  retrieval."*

See the per-module docs for the concepts and the questions they answer.

---

## Next up — Module 4 (RAG)

The most-asked applied-AI interview topic. Build the full pipeline:
retrieve relevant chunks from pgvector (Module 3), stuff them into a prompt
as grounding context, and have the chat model answer **with citations**.
Everything needed (embedding + storage + search + chat model) is already
wired — Module 4 ties them together.
