package com.dileep.ailearning.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A single chat message in the Ollama {@code /api/chat} format.
 *
 * <p>The {@code role} is one of {@code system}, {@code user}, or {@code assistant}.
 * The distinction matters: the <b>system</b> message is the contract/instructions,
 * the <b>user</b> message is the data. (More on that in Module 1.)
 *
 * <p>Module 2 adds {@code images}: a list of <b>base64-encoded</b> images attached
 * to a user turn. That is how Ollama does multimodal input — image + text travel
 * together in one message. {@code @JsonInclude(NON_NULL)} keeps {@code images} out
 * of the JSON entirely for ordinary text messages.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(String role, String content, List<String> images) {

    /** Convenience for the common text-only case (no {@code images} key emitted). */
    public Message(String role, String content) {
        this(role, content, null);
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
        return new Message("user", content, base64Images);
    }
}
