package com.dileep.ailearning.production.dto;

/**
 * An individual token chunk emitted over Server-Sent Events (SSE).
 */
public record StreamTokenEvent(
        String token,
        boolean done,
        Long ttftMs,
        Integer promptTokens,
        Integer responseTokens,
        Long wallMs
) {
}
