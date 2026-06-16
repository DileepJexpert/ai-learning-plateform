package com.dileep.ailearning.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single tool call requested by the model, as returned in an assistant
 * message's {@code tool_calls} array (Module 5).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCall(FunctionCall function) {
}
