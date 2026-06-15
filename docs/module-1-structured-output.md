# Module 1 — Prompt engineering & structured output

**Build:** Harden the invoice extractor so it produces **valid, schema-correct
JSON every time** — strict schema, few-shot, null handling, auto-retry. Done —
see `InvoicePromptFactory`, `InvoiceExtractionService`, `Invoice`/`LineItem`.

---

## The concept in my own words

Getting an LLM to *talk* is easy. Getting it to reliably emit a **fixed JSON
shape my code can deserialize** is the real engineering. Free-form models will
add a "Sure, here's your JSON:" preamble, wrap it in ```` ```json ````, invent
fields, hallucinate values for blanks, or put `₹1,234.50` where I need `1234.50`.
Any of those breaks `objectMapper.readValue(...)`. Module 1 is the set of
techniques that stop all of that.

### The five techniques (and why each exists)

1. **System prompt = the contract; user prompt = the data.**
   The system message holds the schema + rules + examples and never changes. The
   user message is just *this* invoice. Separating them keeps the instructions
   stable and the input clean.

2. **Strict schema in the prompt + `format: "json"`.**
   `format: "json"` makes Ollama constrain decoding so the output is *syntactically
   valid JSON*. But valid JSON ≠ the *right* JSON — a weak model can still emit
   the wrong keys or nest things wrong. So I also **spell the schema out** in the
   prompt and reinforce it. Belt and braces.

3. **Few-shot examples.**
   2–3 input→output pairs. Models pattern-match hard, so a couple of correct
   examples pin down formatting, key names, and edge cases far better than prose
   rules alone. One of my examples deliberately has missing fields → `null`, to
   *teach the null behaviour by demonstration*.

4. **Null handling — never invent.**
   Explicit rule: *if a value isn't on the invoice, output `null`; never guess.*
   Hallucinated values are worse than missing ones in a finance context — a wrong
   GSTIN that looks plausible is a silent data-corruption bug.

5. **Detect invalid output and auto-retry once.**
   Two gates: (a) does it **parse** into my `Invoice` record? (b) does it **pass
   validation** (required fields present, ≥1 line item, no negative amounts)? If
   either fails, I send the model its **own bad output + the exact reason** and
   ask once more. Telling it *why* it failed is far more effective than blindly
   re-asking. Still bad after the retry → reject with a 422, never persist.

### Temperature, top-p, determinism

- **`temperature`** controls randomness. `0` ≈ greedy decoding (always take the
  most likely next token) → as deterministic as the model gets. For
  **extraction I always use 0** — I want the *same* answer for the *same*
  invoice, not creativity. I'd only raise it (0.7–1.0) for generative tasks
  (drafting, brainstorming).
- **`top_p`** (nucleus sampling) is the *other* randomness dial: sample only from
  the smallest set of tokens whose probabilities sum to `p`. Lower `top_p` =
  safer. You typically tune temperature *or* top_p, not both.
- **Determinism/reproducibility:** `temperature 0` + a fixed **`seed`** makes runs
  repeatable, which matters for debugging and tests. (Caveat: still not 100%
  bit-identical across hardware/model versions — local LLMs have some
  nondeterminism — which is *another* reason the validate-and-retry guardrail
  exists.)

### Why `BigDecimal`, not `double`
Money in a payments/ERP system must be exact. `double` can't represent `0.1`
exactly and silently corrupts totals. The model returns plain JSON numbers;
Jackson maps them straight to `BigDecimal`. Small detail, strong signal.

---

## Interview angles

> **"How do you get reliable structured output from an LLM?"**

Four layers: (1) a **strict schema + few-shot** in the system prompt so the model
knows the exact shape; (2) **`format: json` + temperature 0** so output is valid
and deterministic; (3) **deserialize + validate** against a typed schema as a
hard gate; (4) **auto-retry** feeding the model its own error, then **reject**
rather than persist if it still fails. The prompt gets you *most* of the way; the
parse/validate/retry loop is what makes it *production*-reliable.

> **"What is temperature and when would you change it?"**

It's the randomness of token sampling. ~0 → deterministic, best for
extraction/classification/anything with a single correct answer. Higher → more
varied/creative, for drafting or brainstorming. For data extraction I keep it at
0 and add a fixed seed for reproducibility.

> **"Why do you still need schema reinforcement if `format: json` is on?"**

`format: json` only guarantees the output *parses* as JSON — not that it has the
right keys, types, or nesting. Weak/local models will happily produce valid JSON
of the *wrong shape*. The in-prompt schema + few-shot fix the *shape*; validation
catches whatever still slips through.

---

### Resume line
> *"Hardened a local-LLM invoice extractor to emit schema-correct JSON every time
> via strict-schema + few-shot prompting, deterministic decoding (temperature 0 +
> seed), and a parse→validate→auto-retry loop that rejects bad output before
> persistence."*
