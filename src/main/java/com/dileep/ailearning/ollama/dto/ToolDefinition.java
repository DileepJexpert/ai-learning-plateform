package com.dileep.ailearning.ollama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A tool we advertise to the model in the chat request's {@code tools} array
 * (Module 5). Ollama follows the OpenAI-style shape: {@code {"type":"function",
 * "function": {...}}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolDefinition(String type, FunctionDefinition function) {

    public static ToolDefinition of(FunctionDefinition function) {
        return new ToolDefinition("function", function);
    }
}
