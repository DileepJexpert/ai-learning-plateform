package com.dileep.ailearning.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response body from Ollama's {@code POST /api/embed}.
 *
 * <p>The {@code embeddings} field is a list of float arrays — one per input we
 * sent. We always send one input at a time, so we read {@code embeddings[0]}.
 *
 * @param model      model that produced the embedding
 * @param embeddings list of embedding vectors (one per input)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmbedResponse(
        String model,
        List<float[]> embeddings,
        @JsonProperty("total_duration") Long totalDuration
) {

    /** Convenience: get the single embedding when we sent one input. */
    public float[] embedding() {
        if (embeddings == null || embeddings.isEmpty()) {
            throw new IllegalStateException("No embeddings in response");
        }
        return embeddings.getFirst();
    }
}
