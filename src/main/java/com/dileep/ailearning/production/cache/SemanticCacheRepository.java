package com.dileep.ailearning.production.cache;

import com.pgvector.PGvector;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * pgvector-backed storage and retrieval for semantic caching (Module 7).
 *
 * <p>Uses PostgreSQL vector cosine distance {@code <=>} to retrieve previously
 * answered queries whose embedding vector is within a configured similarity threshold.
 */
@Repository
public class SemanticCacheRepository {

    private final JdbcClient jdbc;

    public SemanticCacheRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Store a prompt, generated response, embedding vector, and token usage into the cache.
     */
    public void save(String queryText, String responseText, String model, float[] embedding,
                     Integer promptTokens, Integer responseTokens) {
        jdbc.sql("""
                INSERT INTO semantic_cache (query_text, response_text, model, embedding, prompt_tokens, response_tokens)
                VALUES (?, ?, ?, ?::vector, ?, ?)
                """)
                .param(queryText)
                .param(responseText)
                .param(model)
                .param(new PGvector(embedding).toString())
                .param(promptTokens)
                .param(responseTokens)
                .update();
    }

    /**
     * Find the best matching cached response whose cosine similarity meets or exceeds {@code minSimilarity}.
     *
     * <p>Similarity is calculated as {@code 1 - (embedding <=> queryVector)}.
     */
    public Optional<SemanticCacheEntry> findClosestMatch(float[] queryEmbedding, double minSimilarity) {
        return jdbc.sql("""
                SELECT id, query_text, response_text, model, prompt_tokens, response_tokens, created_at,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM semantic_cache
                WHERE 1 - (embedding <=> ?::vector) >= ?
                ORDER BY embedding <=> ?::vector
                LIMIT 1
                """)
                .param(new PGvector(queryEmbedding).toString())
                .param(new PGvector(queryEmbedding).toString())
                .param(minSimilarity)
                .param(new PGvector(queryEmbedding).toString())
                .query(this::mapEntry)
                .optional();
    }

    /**
     * Count total cached entries in the database.
     */
    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM semantic_cache")
                .query(Long.class)
                .single();
    }

    /**
     * Purge all semantic cache entries.
     */
    public int clear() {
        return jdbc.sql("DELETE FROM semantic_cache")
                .update();
    }

    private SemanticCacheEntry mapEntry(ResultSet rs, int rowNum) throws SQLException {
        var ts = rs.getTimestamp("created_at");
        return new SemanticCacheEntry(
                rs.getLong("id"),
                rs.getString("query_text"),
                rs.getString("response_text"),
                rs.getString("model"),
                rs.getDouble("similarity"),
                rs.getInt("prompt_tokens"),
                rs.getInt("response_tokens"),
                ts != null ? ts.toInstant() : null
        );
    }
}
