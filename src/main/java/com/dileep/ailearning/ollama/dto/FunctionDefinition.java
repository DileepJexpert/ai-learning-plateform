package com.dileep.ailearning.ollama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Describes a callable function to the model (Module 5): its name, a natural-language
 * description (the model reads this to decide when to call it), and a JSON-Schema
 * description of its parameters.
 *
 * @param name        function name, e.g. {@code get_customer}
 * @param description what it does — this is the model's only clue about when to use it
 * @param parameters  JSON Schema object describing the arguments
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FunctionDefinition(String name, String description, Map<String, Object> parameters) {
}
