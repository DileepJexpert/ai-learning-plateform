package com.dileep.ailearning.compliance.dto;

/**
 * Result item from hybrid search combining Reciprocal Rank Fusion (RRF),
 * dense cosine similarity, and sparse BM25 / Full-Text rank.
 */
public record HybridSearchResult(
        Long id,
        String circularId,
        String circularTitle,
        String authority,
        String effectiveDate,
        String clauseRef,
        String sectionTitle,
        String content,
        double rrfScore,
        Double denseScore,
        Double sparseScore
) {
}
