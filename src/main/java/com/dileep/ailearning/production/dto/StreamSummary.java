package com.dileep.ailearning.production.dto;

/**
 * Terminal summary sent at the end of an SSE stream with latency and throughput metrics.
 *
 * @param model        model that streamed the response
 * @param ttftMs       Time-To-First-Token in milliseconds
 * @param tpotMs       Time-Per-Output-Token (ms / token)
 * @param tokensPerSec throughput in tokens per second
 * @param totalTokens  total tokens generated in the response
 * @param wallMs       total wall-clock duration in milliseconds
 */
public record StreamSummary(
        String model,
        long ttftMs,
        double tpotMs,
        double tokensPerSec,
        int totalTokens,
        long wallMs
) {
}
