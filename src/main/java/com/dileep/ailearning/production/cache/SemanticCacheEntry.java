package com.dileep.ailearning.production.cache;

import java.time.Instant;

/**
 * Represents a cached prompt-response pair stored with its embedding vector for semantic lookup.
 *
 * @param id             database primary key
 * @param queryText      original user query/prompt
 * @param responseText   cached generated response
 * @param model          model that produced the response
 * @param similarity     cosine similarity score (populated during lookup, 1.0 = identical)
 * @param promptTokens   input tokens consumed
 * @param responseTokens output tokens generated
 * @param createdAt      timestamp when cached
 */
public record SemanticCacheEntry(
        Long id,
        String queryText,
        String responseText,
        String model,
        double similarity,
        Integer promptTokens,
        Integer responseTokens,
        Instant createdAt
) {
}
