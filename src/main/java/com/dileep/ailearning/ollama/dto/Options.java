package com.dileep.ailearning.ollama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Decoding options passed to Ollama under the {@code "options"} key.
 *
 * <p>Only the knobs we actually use are modelled; null fields are omitted from
 * the JSON so Ollama falls back to its own defaults.
 *
 * @param temperature randomness. 0.0 = deterministic/greedy (use this for extraction).
 *                    Higher (0.7–1.0) = more creative/varied (use for brainstorming, not data).
 * @param topP        nucleus sampling cutoff. Lower = safer/more focused token choices.
 * @param seed        RNG seed. With temperature 0 this gives reproducible output.
 * @param numCtx      context-window size in tokens.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Options(
        Double temperature,
        @JsonProperty("top_p") Double topP,
        Integer seed,
        @JsonProperty("num_ctx") Integer numCtx
) {

    /** Settings tuned for deterministic structured extraction. */
    public static Options forExtraction(double temperature, int seed, int numCtx) {
        return new Options(temperature, null, seed, numCtx);
    }
}
