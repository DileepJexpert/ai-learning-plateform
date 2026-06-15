# Module 2 — Vision / multimodal extraction

**Build:** Send an invoice **photo** to a vision model and extract it into the
*same* `Invoice` schema, through the *same* validate/retry pipeline. Done — see
`extractFromImage(...)` in `InvoiceExtractionService` and
`POST /api/invoices/extract-image`.

> Run it: `ollama pull llama3.2-vision`, then
> `curl -F file=@invoice.jpg localhost:8080/api/invoices/extract-image | jq`

---

## The concept in my own words

A **multimodal** (vision) LLM accepts an image *and* text in the same prompt. In
Ollama's `/api/chat`, you attach images to a user message as an array of
**base64-encoded** strings:

```json
{
  "model": "llama3.2-vision",
  "messages": [
    { "role": "system", "content": "<the same schema contract as Module 1>" },
    { "role": "user",
      "content": "Extract the invoice in this image...",
      "images": ["<base64 of the JPEG>"] }
  ],
  "format": "json"
}
```

The model "reads" the pixels and emits structured text. From my side it's still
the same request/response shape — I just added an `images` field and pointed at a
model that can see.

### Why base64?

The image is **binary**; the API body is **JSON (text)**. Base64 re-encodes
arbitrary bytes into an ASCII-safe string so they can travel inside that JSON
(at ~33% size overhead). My service receives the raw upload bytes and does
`Base64.getEncoder().encodeToString(bytes)` before building the message — that's
the whole trick. (This is the same reason `data:` URLs and email attachments use
base64.)

### The big win: one schema, two front doors

The text path (Module 1) and the image path differ in exactly two things — the
**first user message** and the **model**. Everything downstream is shared: the
schema contract, JSON parsing, Bean-Validation guardrail, and auto-retry. So an
invoice that arrives as a **photo** lands in the identical validated `Invoice`
object as one that arrives as text. That's the "map messy input → a fixed domain
object" principle paying off, and it's why adding vision was a small change.

> Note: I deliberately do **not** resend the image on a retry — once it's in the
> conversation, the correction turn is text-only. Cheaper, and the model already
> "remembers" the picture.

---

## Vision-LLM vs OCR + parser

| | **Vision LLM** (this module) | **Traditional OCR + parser** (e.g. Tesseract + rules) |
|---|---|---|
| **What it does** | Reads pixels → structured JSON in one step | Pixels → raw text, then *you* write parsing logic |
| **Layout handling** | Understands tables/columns/labels semantically | Loses 2-D layout; you rebuild it with regex/heuristics |
| **Varied templates** | Generalises across unseen invoice formats | Brittle — every new vendor layout often = new rules |
| **Setup effort** | A prompt + a schema | OCR tuning + a parser per document type |
| **Accuracy on clean text** | Good | Often excellent (OCR is mature) |
| **Cost / latency** | Heavier model, slower | Light, fast, cheap |
| **Determinism** | Probabilistic (needs validate + retry) | Deterministic given the same text |
| **Hard cases** | Can hallucinate a plausible-but-wrong value | Fails loudly / returns garbage text |

**The honest take:** OCR+rules wins when documents are *uniform and
high-volume* (one fixed template, millions of pages) — it's cheaper, faster, and
auditable. A vision LLM wins when documents are *heterogeneous* (many vendor
layouts, photos, scans, skew) and you'd otherwise be maintaining a zoo of
per-template parsers. A strong **hybrid** is common: OCR for the easy bulk,
escalate the messy/low-confidence ones to a vision model — which is exactly the
cheap-first/escalate routing idea from Module 8.

Because vision output is probabilistic, the **validate + retry + reject**
guardrail isn't optional here — it's what makes a hallucination-prone reader safe
to put in front of a ledger.

---

## Interview angles

> **"How would you build document/invoice extraction?"**

Map every input modality onto one validated domain object. Receive the document
(text, or a photo I base64-encode), send it to an LLM under a **strict schema +
few-shot** contract with `format: json` and temperature 0, then **deserialize →
validate → auto-retry → reject** so bad output never reaches the DB. For images I
use a vision model (e.g. `llama3.2-vision`); the entire downstream pipeline is
shared with the text path. At scale I'd add cheap-first routing and async
processing (Modules 7–8).

> **"Vision model vs OCR + parser — which and why?"**

Depends on document *diversity*. Uniform, high-volume, one template → OCR + rules
(cheaper, faster, deterministic, auditable). Heterogeneous layouts / photos /
scans → vision LLM (it generalises instead of needing a parser per template). In
practice, hybrid: OCR the bulk, escalate low-confidence/messy pages to the vision
model. And whatever the reader, wrap it in schema validation + retry because the
model's output is probabilistic.

---

### Resume line
> *"Extended the local-LLM invoice extractor to multimodal input: base64 image
> upload → vision model (llama3.2-vision) → the same schema-validated domain
> object and auto-retry pipeline as the text path."*
