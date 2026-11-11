package com.dileep.ailearning.compliance.model;

/**
 * An individual audited compliance finding paired directly with its source clause reference.
 *
 * @param finding      the factual compliance statement extracted or synthesized
 * @param circularId   source circular reference
 * @param clauseRef    hierarchical clause pointer (e.g. "Section 4.2(a)")
 * @param confidence   confidence score (0.0 to 1.0)
 */
public record ClauseStatement(
        String finding,
        String circularId,
        String clauseRef,
        double confidence
) {
}
