# Module 0 — Baseline: calling a local model

**Build:** A Spring Boot service that calls a local Ollama model over HTTP
(`POST /api/chat`). Done — see `OllamaClient` and `ChatController`.

---

## The concept in my own words

An LLM, from a backend engineer's point of view, is just an **HTTP service that
turns text into text**. Ollama runs that service on my own machine at
`http://localhost:11434`. I POST a list of messages (`system`, `user`,
`assistant`) and a model name; I get back the assistant's reply plus some
metadata (token counts, durations). There is no magic — it's a request/response
integration like any other downstream dependency, except it's slow,
non-deterministic, and priced in *tokens*.

The key wire concepts:

- **Messages & roles.** `system` = standing instructions/contract, `user` = the
  input, `assistant` = the model's turns. Multi-turn = send the growing list back.
- **`stream`.** `false` → one complete JSON response. `true` → token-by-token
  (Module 7).
- **`options`.** Decoding knobs: `temperature`, `top_p`, `seed`, `num_ctx`.
- **Token counts.** `prompt_eval_count` (input) and `eval_count` (output) — what
  you'd bill/log on. I log these on every call.

## Local vs hosted inference — the real tradeoff

| | **Local (Ollama)** | **Hosted API (e.g. a cloud LLM)** |
|---|---|---|
| **Data residency** | Never leaves the box → air-gapped/regulated OK | Data goes to a third party |
| **Cost** | Fixed (your hardware), no per-token bill | Per-token; scales with usage |
| **Latency** | No network hop, but bound by local GPU/CPU | Network + provider queue |
| **Quality** | Smaller models (3B–14B) | Frontier models, much stronger |
| **Ops** | You run/patch/scale it | Provider handles it |
| **Control** | Full (pin versions, offline) | At provider's mercy (deprecations) |

## Interview angle

> **"When would you run a model locally vs call a hosted API?"**

Run **local** when **data residency / privacy** dominates: regulated or
air-gapped environments (banking, healthcare, defence), where customer data
legally or contractually can't go to a third party — and when you want
**predictable cost** (no per-token bill) and **version control** (the model
can't be deprecated under you). The price is weaker models and you own the ops.

Call **hosted** when you need **frontier quality**, **elastic scale**, or want
**zero infra**, and the data is allowed to leave.

A strong real example: *"For an invoice-extraction feature in a fintech, I'd run
a local model — the documents contain customer PII and account data that
shouldn't leave our boundary, the volume makes per-token pricing painful, and a
7B–14B model is plenty for structured extraction."* Most candidates can't give a
crisp data-residency answer; that's the differentiator.

---

### Resume line
> *"Built a Spring Boot service integrating a local LLM (Ollama) over its REST
> API, with typed config, token/latency logging, and clean failure handling for
> an unreachable model backend."*
