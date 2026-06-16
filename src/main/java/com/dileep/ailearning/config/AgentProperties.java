package com.dileep.ailearning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for Module 5 (tool-calling agent), bound from {@code agent.*}.
 *
 * @param model         a tool-capable chat model (Qwen2.5 and Llama 3.1+ support tools)
 * @param temperature   low — we want deterministic tool selection, not creativity
 * @param seed          fixed seed for reproducibility
 * @param numCtx        context window (grows as tool results accumulate)
 * @param maxIterations safety cap on the agentic loop — prevents infinite tool-calling
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        @DefaultValue("qwen2.5-coder:7b") String model,
        @DefaultValue("0.0") double temperature,
        @DefaultValue("42") int seed,
        @DefaultValue("8192") int numCtx,
        @DefaultValue("5") int maxIterations
) {
}
