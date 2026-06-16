package com.dileep.ailearning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for Module 6 (evaluation & guardrails), bound from {@code eval.*}.
 *
 * @param judgeModel     model used as an LLM-judge for fuzzy outputs
 * @param judgePassScore minimum 1–5 judge score to count as a pass
 * @param minAccuracy    field-accuracy threshold an extraction eval run should beat (for CI gating)
 */
@ConfigurationProperties(prefix = "eval")
public record EvalProperties(
        @DefaultValue("qwen2.5-coder:7b") String judgeModel,
        @DefaultValue("4") int judgePassScore,
        @DefaultValue("0.8") double minAccuracy
) {
}
