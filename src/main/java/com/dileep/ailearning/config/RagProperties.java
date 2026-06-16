package com.dileep.ailearning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for Module 4 (RAG), bound from {@code rag.*}.
 *
 * @param chatModel       chat model that writes the grounded answer
 * @param temperature     low for grounded QA — we want faithful answers, not creativity
 * @param seed            fixed seed for reproducibility
 * @param numCtx          context window; RAG stuffs a lot of text, so keep it generous
 * @param topK            how many chunks to actually put in the prompt (after re-ranking)
 * @param fetchK          larger candidate pool retrieved before re-ranking
 * @param rerank          whether to MMR re-rank the candidate pool (vs. take top-k by similarity)
 * @param mmrLambda       MMR relevance/diversity balance: 1.0 = pure relevance, 0.0 = pure diversity
 * @param maxContextChars budget for the stuffed context (rough proxy for token budget; ~4 chars/token)
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        @DefaultValue("qwen2.5-coder:7b") String chatModel,
        @DefaultValue("0.1") double temperature,
        @DefaultValue("42") int seed,
        @DefaultValue("8192") int numCtx,
        @DefaultValue("4") int topK,
        @DefaultValue("20") int fetchK,
        @DefaultValue("true") boolean rerank,
        @DefaultValue("0.6") double mmrLambda,
        @DefaultValue("6000") int maxContextChars
) {
}
