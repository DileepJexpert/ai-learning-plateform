package com.dileep.ailearning.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single chat message in the Ollama {@code /api/chat} format.
 *
 * <p>The {@code role} is one of {@code system}, {@code user}, or {@code assistant}.
 * The distinction matters: the <b>system</b> message is the contract/instructions,
 * the <b>user</b> message is the data. (More on that in Module 1.)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(String role, String content) {

    public static Message system(String content) {
        return new Message("system", content);
    }

    public static Message user(String content) {
        return new Message("user", content);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content);
    }
}
