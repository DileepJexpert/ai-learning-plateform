package com.dileep.ailearning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Typed configuration for talking to a local Ollama instance (Module 0).
 *
 * <p>Bound from the {@code ollama.*} keys in {@code application.yml}. Using a
 * record + {@code @ConfigurationProperties} gives us immutable, validated,
 * IDE-discoverable config instead of scattered {@code @Value} strings.
 *
 * @param baseUrl        root URL of the Ollama server, e.g. {@code http://localhost:11434}
 * @param model          default model tag, e.g. {@code qwen2.5-coder:7b}
 * @param connectTimeout how long to wait to establish a TCP connection
 * @param readTimeout    how long to wait for the model to respond (LLMs are slow — keep this generous)
 */
@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
        @DefaultValue("http://localhost:11434") String baseUrl,
        @DefaultValue("qwen2.5-coder:7b") String model,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("120s") Duration readTimeout
) {
}
