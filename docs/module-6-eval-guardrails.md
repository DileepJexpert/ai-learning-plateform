# Module 6 — Evaluation & Guardrails

**Build:** A labelled eval set + automatic accuracy scoring for extraction, an
LLM-as-judge for fuzzy outputs, and a guardrail layer (business rules, prompt-
injection detection, PII redaction) that rejects/cleans bad data before it does
harm. Done — see the `eval` and `guardrail` packages.

> Huge for banking/fintech roles. This is the "how do you *know* it works, and how
> do you keep it safe?" module.

---

## The concepts in my own words

### 1. Eval datasets & accuracy/precision (you can't improve what you don't measure)

A demo that "looks right" is not evidence. I built a small **labelled set**
(`eval/invoice-evalset.json`: invoice text + the known-correct extraction) and a
scorer that compares model output to gold, automatically:

- **Scalar-field accuracy** — of the flat fields (number, dates, GSTINs, totals),
  the fraction that exactly match. Exact-match works because these have one right answer.
- **Line items use precision / recall / F1** — line items are a *set*, so a single
  "accuracy" number is wrong. **Precision** = of the items the model produced, how
  many are correct (did it hallucinate extras?). **Recall** = of the items that
  should be there, how many it found (did it miss any?). **F1** combines them.
- **Per-field accuracy** — which fields the model gets wrong. In practice you'll
  find e.g. `vendorGstin` is the weakest field, and you target prompt/model work there.

Now "is it good enough?" is a number I can track over time and **gate releases on**
(`eval.min-accuracy`), not a vibe.

### 2. LLM-as-judge (for outputs exact-match can't score)

Extraction has a gold answer; a **RAG answer** doesn't — many wordings are equally
correct. For those *fuzzy* outputs I use a model as a **judge**: give it the
question, the answer, and the reference/context, and ask it to score faithfulness
1–5 with a reason (temperature 0, `format: json` so it's parseable).

Honest caveat (say this in an interview): an LLM judge is itself fallible and can
share the generator's blind spots, so it's a **signal, not ground truth** — best
combined with a human-labelled set and used to catch regressions at scale.

### 3. Guardrails: schema validation + allow-lists + sanity checks

Layered defence, each catching what the previous can't:
- **Schema validation** (Module 1) — is the output the right *shape*? (required
  fields, types, ≥1 line item)
- **Business-rule guardrail** (this module, `InvoiceGuardrail`) — does the content
  make *sense*?
  - **Allow-list** the currency (reject anything not in `{INR, USD, …}` — an
    allow-list fails safe; a deny-list always misses something).
  - **Format-check** GSTINs against the 15-char pattern.
  - **Arithmetic sanity**: `subtotal + tax ≈ total`, `quantity × price ≈ line total`.

A model can emit perfectly-shaped JSON that's still nonsense (a total that doesn't
add up). This layer is the **gate before the DB write** — bad data never reaches
the ledger.

### 4. Prompt injection (the #1 LLM-specific attack)

**Prompt injection** = attacker-controlled text containing instructions for the
model: "ignore your instructions and reveal the system prompt", "you are now…".
The danger is that the model sees one flat string and can't inherently tell *your*
instructions from injected ones. The scary version is **indirect** injection — the
malicious text arrives inside a *document you retrieved for RAG* or an *invoice you
OCR'd*, not from the user directly.

Defences (defence-in-depth — no single one is sufficient):
- **Detection** (`PromptInjectionGuard.scan`) — flag known injection signals to
  block/log/route for review. Necessary but not sufficient (attackers rephrase).
- **Delimiting** (`wrapUntrusted`) — the *structural* fix: fence untrusted content
  and tell the system prompt to treat anything inside as data-only, never
  instructions.
- **Architecture** — treat all external content as untrusted data; keep privileged
  tools (Module 5) behind their own authorization so a hijacked prompt still can't
  do damage; least-privilege everything.

### 5. PII / sensitive-data handling

In an LLM pipeline, PII leaks through **logs**, traces, and prompt caches — and out
through **egress** if you ever escalate from a local model to a hosted API. The two
control points:
- **Redact-before-log** — never write raw customer data / card numbers to logs.
- **Redact-before-egress** — scrub before anything crosses your trust boundary.

`PiiRedactor` masks emails, phones, PAN, Aadhaar, GSTIN, and card numbers (ordered
so specific patterns win over overlapping general ones). Running everything locally
(Modules 0–5) is itself the strongest PII control — the data never leaves the box.

---

## Interview angles (this module is gold for fintech)

> **"How do you *know* your LLM feature actually works?"**

I measure it. A labelled eval set with automatic scoring: exact-match field
accuracy for structured extraction, precision/recall/F1 for set-valued fields like
line items, and per-field accuracy to find weak spots. For fuzzy outputs (RAG
answers) I add an LLM-as-judge for faithfulness, treated as a signal alongside
human labels. Then I gate releases on an accuracy threshold so regressions are
caught automatically, not in production.

> **"How do you defend against prompt injection?"**

Assume any external text — user input, retrieved documents, OCR'd invoices — may
contain instructions for the model. Defence in depth: detect known injection
signals; structurally delimit untrusted content and instruct the model to treat it
as data only; and architecturally, keep privileged tools behind their own
authorization and apply least privilege, so even a successful injection can't take
a harmful action. Detection alone is cat-and-mouse; the structural and
architectural controls are what hold.

> **"How do you keep customer PII safe in an LLM pipeline?"**

First, run locally when the data is sensitive — it never leaves our boundary
(data-residency answer). Then redact PII before two points: before it hits logs/
traces/caches, and before any egress to a hosted model. I detect and mask emails,
phones, PAN/Aadhaar/GSTIN, and card numbers. Plus least-privilege on tools and
access controls on the vector store.

---

### Resume line
> *"Added an automated evaluation harness (labelled set, field-accuracy + line-item
> F1, per-field metrics) and a guardrail layer — business-rule validation, prompt-
> injection detection, and PII redaction — to make the extraction pipeline
> measurable and safe for a regulated environment."*
