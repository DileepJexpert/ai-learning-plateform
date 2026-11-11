package com.dileep.ailearning.production.dto;

/**
 * Production chat response carrying latency, cache provenance, and failover metadata.
 *
 * @param reply               the generated or retrieved text
 * @param model               model used to produce or retrieve the answer
 * @param cached              whether this response was served from cache
 * @param cacheType           cache tier (NONE, EXACT, SEMANTIC)
 * @param semanticSimilarity  cosine similarity if served from semantic cache (1.0 = identical)
 * @param fallbackUsed        whether failover routing was triggered due to primary model error
 * @param promptTokens        input tokens (served or saved)
 * @param responseTokens      output tokens (served or saved)
 * @param wallMs              wall-clock response time in milliseconds
 */
public record ProductionChatResponse(
        String reply,
        String model,
        boolean cached,
        String cacheType,
        Double semanticSimilarity,
        boolean fallbackUsed,
        Integer promptTokens,
        Integer responseTokens,
        Long wallMs
) {
}
