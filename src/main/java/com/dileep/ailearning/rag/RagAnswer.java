package com.dileep.ailearning.rag;

import java.util.List;

/**
 * The result of a RAG query (Module 4): the grounded answer plus the sources it
 * was built from and observability metadata.
 *
 * <p>Returning {@code sources} is the <b>attribution</b> half of RAG — the caller
 * (and the model's [n] citations) can be traced back to exact document chunks.
 * This is what makes a RAG answer auditable rather than a black box.
 *
 * @param answer         the model's grounded answer (contains [n] citation markers)
 * @param sources        the chunks stuffed into the prompt, numbered to match the [n] markers
 * @param model          chat model used
 * @param retrieved      how many candidate chunks were retrieved before re-ranking
 * @param used           how many chunks were actually put in the prompt (after re-rank + budget)
 * @param reranked       whether MMR re-ranking was applied
 * @param durationMs     wall-clock time for the whole RAG call
 * @param promptTokens   input tokens (context + question)
 * @param responseTokens output tokens
 */
public record RagAnswer(
        String answer,
        List<Citation> sources,
        String model,
        int retrieved,
        int used,
        boolean reranked,
        long durationMs,
        Integer promptTokens,
        Integer responseTokens
) {

    /**
     * One cited source.
     *
     * @param ref        the [n] number used in the answer and context
     * @param docName    which document the chunk came from
     * @param chunkIndex the chunk's position within that document
     * @param similarity cosine similarity to the query (1.0 = identical meaning)
     * @param snippet    a short preview of the chunk text
     */
    public record Citation(int ref, String docName, int chunkIndex, double similarity, String snippet) {
    }
}
