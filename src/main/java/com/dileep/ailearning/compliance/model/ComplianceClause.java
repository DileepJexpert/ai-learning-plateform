package com.dileep.ailearning.compliance.model;

import java.time.Instant;

/**
 * A discrete regulatory or compliance clause (e.g. from an RBI, OCC, or Basel III circular).
 *
 * @param id             database primary key
 * @param circularId     regulatory reference ID (e.g. "RBI/2024-25/112")
 * @param circularTitle  official title of the circular or regulation
 * @param authority      regulatory body (e.g. "Reserve Bank of India", "OCC", "Basel Committee")
 * @param effectiveDate  effective date of the rule (e.g. "2024-04-01")
 * @param clauseRef      hierarchical section / clause pointer (e.g. "Section 4.2(a)")
 * @param sectionTitle   subheading or topic of the clause
 * @param content        exact clause text (PII-sanitized)
 * @param embedding      768-dim float vector for nomic-embed-text
 * @param createdAt      timestamp when ingested
 */
public record ComplianceClause(
        Long id,
        String circularId,
        String circularTitle,
        String authority,
        String effectiveDate,
        String clauseRef,
        String sectionTitle,
        String content,
        float[] embedding,
        Instant createdAt
) {
    public ComplianceClause(String circularId, String circularTitle, String authority,
                            String effectiveDate, String clauseRef, String sectionTitle,
                            String content, float[] embedding) {
        this(null, circularId, circularTitle, authority, effectiveDate, clauseRef, sectionTitle, content, embedding, Instant.now());
    }
}
