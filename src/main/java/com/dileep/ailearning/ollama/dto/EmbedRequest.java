package com.dileep.ailearning.ollama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for Ollama's {@code POST /api/embed} (Module 3).
 *
 * <p>Unlike chat, the embed endpoint takes a single {@code input} string (or an
 * array — we send one at a time for simplicity) and returns a float array.
 *
 * @param model model tag, e.g. {@code nomic-embed-text}
 * @param input the text to embed
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmbedRequest(String model, String input) {
}
