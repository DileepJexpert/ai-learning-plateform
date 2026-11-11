package com.dileep.ailearning.compliance.model;

/**
 * An immutable citation pointing auditors directly to the source regulatory clause.
 */
public record AuditCitation(
        String circularId,
        String circularTitle,
        String authority,
        String effectiveDate,
        String clauseRef,
        String sectionTitle,
        String excerpt
) {
}
