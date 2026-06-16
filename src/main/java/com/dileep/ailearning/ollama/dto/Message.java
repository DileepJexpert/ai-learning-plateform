package com.dileep.ailearning.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A single chat message in the Ollama {@code /api/chat} format.
 *
 * <p>The {@code role} is one of {@code system}, {@code user}, {@code assistant},
 * or {@code tool}. The distinction matters: the <b>system</b> message is the
 * contract/instructions, the <b>user</b> message is the data (Module 1).
 *
 * <p>Module 2 added {@code images} (base64) for multimodal input. Module 5 adds
 * {@code toolCalls} (populated on an <i>assistant</i> turn when the model wants to
 * call a function) and {@code toolName} (set on a <i>tool</i> turn carrying a
 * tool's result back to the model). {@code @JsonInclude(NON_NULL)} keeps all the
 * optional fields out of the JSON unless they're set.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(
        String role,
        String content,
        List<String> images,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,
        @JsonProperty("tool_name") String toolName
) {

    /** Convenience for the common text-only case. */
    public Message(String role, String content) {
        this(role, content, null, null, null);
    }

    public static Message system(String content) {
        return new Message("system", content);
    }

    public static Message user(String content) {
        return new Message("user", content);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content);
    }

    /** Module 2: a user turn carrying one or more base64-encoded images. */
    public static Message userWithImages(String content, List<String> base64Images) {
        return new Message("user", content, base64Images, null, null);
    }

    /** Module 5: a tool-result turn fed back to the model after executing a tool. */
    public static Message toolResult(String toolName, String content) {
        return new Message("tool", content, null, null, toolName);
    }

    /** True if this (assistant) message asked to call one or more tools. */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
