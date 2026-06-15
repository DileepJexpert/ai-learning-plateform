package com.dileep.ailearning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tuning knobs for the invoice extractor, bound from {@code invoice.extraction.*}.
 *
 * <p>These are the decoding-time settings that make structured extraction
 * <em>reliable</em> and <em>reproducible</em>:
 *
 * @param model       text model for Module 1 extraction (e.g. {@code qwen2.5-coder:7b};
 *                    escalate to {@code qwen2.5-coder:14b} for hard documents)
 * @param visionModel multimodal model for Module 2 image extraction
 *                    (must accept images, e.g. {@code llama3.2-vision})
 * @param temperature 0.0 = greedy/deterministic. For extraction you never want randomness.
 * @param seed        fixed RNG seed; with temperature 0 this makes runs reproducible.
 * @param numCtx      context window (tokens) the model may use for prompt + output.
 * @param maxRetries  how many times to re-ask after invalid/unschema'd output.
 *                    1 == "auto-retry once" (2 attempts total).
 */
@ConfigurationProperties(prefix = "invoice.extraction")
public record InvoiceExtractionProperties(
        @DefaultValue("qwen2.5-coder:7b") String model,
        @DefaultValue("llama3.2-vision") String visionModel,
        @DefaultValue("0.0") double temperature,
        @DefaultValue("42") int seed,
        @DefaultValue("8192") int numCtx,
        @DefaultValue("1") int maxRetries
) {
}
