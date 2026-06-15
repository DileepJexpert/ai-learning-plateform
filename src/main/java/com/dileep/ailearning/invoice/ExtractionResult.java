package com.dileep.ailearning.invoice;

import com.dileep.ailearning.invoice.model.Invoice;

/**
 * A successful extraction plus the metadata that makes the pipeline observable.
 *
 * <p>{@code attempts} surfaces whether the auto-retry kicked in; the token counts
 * and {@code durationMs} are the raw material for cost/latency accounting later
 * (Module 7).
 */
public record ExtractionResult(
        Invoice invoice,
        String model,
        int attempts,
        long durationMs,
        Integer promptTokens,
        Integer responseTokens
) {
}
