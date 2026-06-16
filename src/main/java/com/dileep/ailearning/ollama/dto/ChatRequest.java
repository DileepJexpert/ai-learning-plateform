package com.dileep.ailearning.ollama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Request body for Ollama's {@code POST /api/chat}.
 *
 * @param model    model tag, e.g. {@code qwen2.5-coder:7b}
 * @param messages the conversation so far (system / user / assistant / tool turns)
 * @param stream   we set this {@code false} to get one complete response (Module 7 covers streaming)
 * @param format   output format. {@code "json"} asks Ollama to constrain decoding to valid JSON.
 *                 Left {@code null} for free-form text. (Newer Ollama can also take a full JSON
 *                 schema object here — see the README.)
 * @param options  decoding options (temperature, seed, ...)
 * @param tools    Module 5: functions the model may call. Null/omitted for plain chat.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
        String model,
        List<Message> messages,
        boolean stream,
        Object format,
        Options options,
        List<ToolDefinition> tools
) {

    /** Free-form (text) chat — Module 0 baseline. */
    public static ChatRequest text(String model, List<Message> messages, Options options) {
        return new ChatRequest(model, messages, false, null, options, null);
    }

    /** JSON-constrained chat — what Module 1 extraction uses ({@code format: "json"}). */
    public static ChatRequest json(String model, List<Message> messages, Options options) {
        return new ChatRequest(model, messages, false, "json", options, null);
    }

    /** Tool-enabled chat — Module 5 agent loop. */
    public static ChatRequest withTools(String model, List<Message> messages, Options options,
                                        List<ToolDefinition> tools) {
        return new ChatRequest(model, messages, false, null, options, tools);
    }
}
