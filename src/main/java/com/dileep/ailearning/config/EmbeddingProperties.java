package com.dileep.ailearning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed configuration for Module 3 (embeddings &amp; semantic search), bound from
 * {@code embedding.*} in {@code application.yml}.
 *
 * @param model      embedding model to use (e.g. {@code nomic-embed-text}).
 *                   The number of dimensions the model emits determines the
 *                   pgvector column width.
 * @param dimensions vector dimension count. Must match the model's output
 *                   (768 for nomic-embed-text). Used by the schema DDL and
 *                   as a sanity check.
 * @param chunkSize  target chunk size in characters for splitting documents.
 * @param chunkOverlap  character overlap between consecutive chunks — keeps
 *                      context at chunk boundaries.
 */
@ConfigurationProperties(prefix = "embedding")
public record EmbeddingProperties(
        @DefaultValue("nomic-embed-text") String model,
        @DefaultValue("768") int dimensions,
        @DefaultValue("500") int chunkSize,
        @DefaultValue("100") int chunkOverlap
) {
}
