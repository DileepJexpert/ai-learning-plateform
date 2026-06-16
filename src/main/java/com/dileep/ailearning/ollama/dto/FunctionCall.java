package com.dileep.ailearning.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * The function the model wants to call, inside a {@link ToolCall} (Module 5).
 *
 * <p>Note: Ollama returns {@code arguments} as a JSON <b>object</b> (a map),
 * unlike some APIs that return a stringified JSON. So we model it as a Map.
 *
 * @param name      the tool/function name the model chose
 * @param arguments the arguments the model supplied (may be malformed — we validate)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FunctionCall(String name, Map<String, Object> arguments) {
}
