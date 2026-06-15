# AI Learning Platform — Applied AI for Backend Engineers

A runnable, interview-ready reference project for orchestrating **local LLMs (Ollama)**
from **Spring Boot**. You're not training models here — you're *orchestrating* them.

This repo follows a module-by-module learning plan. What's built **right now**:

| Module | Topic | Status |
|--------|-------|--------|
| **0** | Baseline — call a local model over HTTP | ✅ built |
| **1** | Prompt engineering & **structured output** (hardened invoice extractor) | ✅ built |
| 2 | Vision / multimodal extraction | ⏳ next |
| 3–8 | Embeddings, RAG, tools/agents, eval, production, system design | 🗺️ planned |

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
   # optional alternates:
   ollama pull qwen2.5-coder:14b   # max quality
   ollama pull llama3.2:3b         # fast smoke tests
   ```
   Nothing leaves your machine — that's the whole point of local inference
   (privacy / cost / control; great for regulated or air-gapped environments).

No Maven install needed — use the bundled wrapper (`./mvnw`).

---

## Quick start

```bash
# 1. run the tests (these DON'T need Ollama — the model is mocked)
./mvnw test

# 2. start the service (this DOES need Ollama running)
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

---

## Project layout

```
src/main/java/com/dileep/ailearning/
├─ AiLearningApplication.java        # entry point
├─ config/                           # typed config (OllamaProperties, ...) + RestClient bean
├─ ollama/                           # MODULE 0: the only code that talks to Ollama
│  ├─ OllamaClient.java              #   thin wrapper over POST /api/chat (logs token usage)
│  └─ dto/                           #   the wire format (ChatRequest/Response, Message, Options)
├─ chat/ChatController.java          # MODULE 0: POST /api/chat demo endpoint
├─ invoice/                          # MODULE 1: structured extraction
│  ├─ InvoicePromptFactory.java      #   the schema + few-shot contract  ← the "feature"
│  ├─ InvoiceExtractionService.java  #   parse + validate + auto-retry loop
│  ├─ InvoiceController.java         #   POST /api/invoices/extract[-sample]
│  └─ model/{Invoice,LineItem}.java  #   target schema + validation guardrails
└─ common/                           # JsonSanitizer, GlobalExceptionHandler (RFC-7807)
```

`docs/` has a one-page concept note per module ([Module 0](docs/module-0-baseline.md),
[Module 1](docs/module-1-structured-output.md)) — written as interview prep.

---

## Configuration

All in [`application.yml`](src/main/resources/application.yml); override via env vars or flags:

| Key | Default | Meaning |
|-----|---------|---------|
| `ollama.base-url` | `http://localhost:11434` | where Ollama listens |
| `ollama.model` | `qwen2.5-coder:7b` | default chat model |
| `invoice.extraction.model` | `qwen2.5-coder:7b` | model for extraction (escalate to `:14b` for hard docs) |
| `invoice.extraction.temperature` | `0.0` | 0 = deterministic; never raise for extraction |
| `invoice.extraction.seed` | `42` | fixed seed → reproducible runs |
| `invoice.extraction.max-retries` | `1` | "auto-retry once" |

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

See the per-module docs for the concepts and the questions they answer.

---

## Next up — Module 2 (Vision)

`ollama pull llama3.2-vision`, send a base64 invoice **photo**, reuse the exact
same `Invoice` schema and validation/retry pipeline. The extraction service is
already model- and input-agnostic, so most of this plugs straight in.
