package com.dileep.ailearning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration for Module 7 (Production Concerns): streaming, caching,
 * fallback routing, and latency observability.
 *
 * @param cacheEnabled           whether exact and semantic response caching is active
 * @param similarityThreshold    minimum cosine similarity (0.0 to 1.0) to qualify as a semantic cache hit
 * @param fallbackEnabled        whether to route to a secondary model if the primary fails/times out
 * @param fallbackModel          secondary model tag to failover to (e.g. fast SLM like {@code llama3.2:3b})
 * @param streamingTimeout       timeout duration for SSE streaming connections
 */
@ConfigurationProperties(prefix = "production")
public record ProductionProperties(
        @DefaultValue("true") boolean cacheEnabled,
        @DefaultValue("0.90") double similarityThreshold,
        @DefaultValue("true") boolean fallbackEnabled,
        @DefaultValue("llama3.2:3b") String fallbackModel,
        @DefaultValue("60s") Duration streamingTimeout
) {
}
