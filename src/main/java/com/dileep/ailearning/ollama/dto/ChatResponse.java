package com.dileep.ailearning.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body from Ollama's {@code POST /api/chat} (non-streaming).
 *
 * <p>We keep the few fields we care about. The token counts and durations are
 * gold for Module 7 (cost/latency accounting) — we log them on every call.
 *
 * @param model           model that produced the answer
 * @param message         the assistant's reply (its {@code content} is the text/JSON we want)
 * @param done            whether generation finished
 * @param totalDuration   end-to-end time in <b>nanoseconds</b>
 * @param promptEvalCount tokens consumed by the prompt (input tokens)
 * @param evalCount       tokens generated in the response (output tokens)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatResponse(
        String model,
        Message message,
        boolean done,
        @JsonProperty("total_duration") Long totalDuration,
        @JsonProperty("prompt_eval_count") Integer promptEvalCount,
        @JsonProperty("eval_count") Integer evalCount
) {

    /** Convenience: the assistant's text content, or empty string if absent. */
    public String content() {
        return message == null || message.content() == null ? "" : message.content();
    }
}
