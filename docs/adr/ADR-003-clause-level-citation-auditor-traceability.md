# ADR-003: Mandatory Clause-Level Citation Attribution and Strict Refusal Contract

* **Status:** Accepted
* **Date:** 2026-11-11
* **Context:** Regulatory & Compliance Document Engine (Blueprint 1)

---

## Context and Problem Statement

In standard consumer generative AI, models produce fluent prose with vague or nonexistent sourcing ("According to guidelines..."). 

In regulated banking, financial examination, and risk audit (e.g. Federal Reserve SR 11-7, OCC Model Risk Management guidelines, RBI IT Examinations), ungrounded summaries are a critical liability. An internal auditor, risk manager, or external regulator must be able to trace every single factual statement directly to an official clause in an authoritative circular.

Furthermore, when the circular corpus does not contain the answer, hallucinating a plausible answer is catastrophic. The model must refuse deterministically.

---

## Decision Outcome

**Chosen Solution:** **Clause-Level Structured Citation Attribution & Deterministic Refusal Contract**.

1. **Granular Chunk Metadata:** Rather than treating documents as arbitrary byte splits, regulatory texts are chunked along natural regulatory boundaries (`circularId`, `authority`, `effectiveDate`, `clauseRef`, `sectionTitle`).
2. **Auditor Prompt Contract:**
   * The model is instructed as an Audit AI.
   * Output format is constrained via JSON schema to an executive `summary` and a list of granular `findings`.
   * Each finding MUST carry an explicit `circularId` and `clauseRef`.
3. **Refusal Protocol:**
   * If the retrieved regulatory context is empty or does not address the question, the system sets `summary` to `"INSUFFICIENT_REGULATORY_GROUNDING"` and emits zero hallucinated findings.
4. **Verified Citation Manifest:**
   * The response payload includes an audited list of `verifiedCitations` carrying full source metadata and textual excerpts.

---

## Consequences

* **Positive:**
  * Complete non-repudiation and auditability: Compliance officers can click directly from a generated finding to the exact paragraph of the RBI or Basel III circular.
  * Meets Model Risk Management (MRM) SR 11-7 expectations for explainability and provenance.
* **Negative:**
  * Constrained decoding and few-shot formatting require slightly higher token budgets compared to raw free-text generation.
