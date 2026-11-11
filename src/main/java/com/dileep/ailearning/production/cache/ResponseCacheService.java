package com.dileep.ailearning.production.cache;

import com.dileep.ailearning.config.EmbeddingProperties;
import com.dileep.ailearning.config.ProductionProperties;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.EmbedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-tier cache service (Module 7):
 * <ol>
 *   <li><b>Tier 1: Exact Hash Cache</b> — O(1) in-memory lookup via SHA-256 hash of the prompt.
 *       Response time: &lt;1ms, 0 cost.</li>
 *   <li><b>Tier 2: Semantic Cache</b> — pgvector cosine similarity lookup via query embedding.
 *       Matches rephrased queries (e.g. "GST on laptop" vs "tax rate for laptops")
 *       when similarity &ge; threshold.</li>
 * </ol>
 */
@Service
public class ResponseCacheService {

    private static final Logger log = LoggerFactory.getLogger(ResponseCacheService.class);

    public enum CacheType {
        NONE, EXACT, SEMANTIC
    }

    public record CacheLookupResult(
            boolean hit,
            String responseText,
            String model,
            CacheType cacheType,
            double similarity,
            Integer promptTokens,
            Integer responseTokens
    ) {
        public static CacheLookupResult miss() {
            return new CacheLookupResult(false, null, null, CacheType.NONE, 0.0, null, null);
        }

        public static CacheLookupResult exact(String responseText, String model, Integer promptTokens, Integer responseTokens) {
            return new CacheLookupResult(true, responseText, model, CacheType.EXACT, 1.0, promptTokens, responseTokens);
        }

        public static CacheLookupResult semantic(String responseText, String model, double similarity,
                                                 Integer promptTokens, Integer responseTokens) {
            return new CacheLookupResult(true, responseText, model, CacheType.SEMANTIC, similarity, promptTokens, responseTokens);
        }
    }

    private record ExactEntry(String responseText, String model, Integer promptTokens, Integer responseTokens, Instant cachedAt) {
    }

    private final Map<String, ExactEntry> exactCache = new ConcurrentHashMap<>();
    private final SemanticCacheRepository semanticRepo;
    private final OllamaClient ollama;
    private final String embeddingModel;
    private final ProductionProperties productionProps;

    public ResponseCacheService(SemanticCacheRepository semanticRepo,
                                OllamaClient ollama,
                                EmbeddingProperties embeddingProps,
                                ProductionProperties productionProps) {
        this.semanticRepo = semanticRepo;
        this.ollama = ollama;
        this.embeddingModel = embeddingProps.model();
        this.productionProps = productionProps;
    }

    /**
     * Look up a response in the exact cache first, then the semantic cache.
     *
     * @param queryText raw user prompt
     * @param model     target LLM model tag
     * @return {@link CacheLookupResult} with hit details or a miss
     */
    public CacheLookupResult get(String queryText, String model) {
        if (!productionProps.cacheEnabled()) {
            return CacheLookupResult.miss();
        }

        // Tier 1: Exact Hash Lookup
        String key = hashKey(model, queryText);
        ExactEntry exact = exactCache.get(key);
        if (exact != null) {
            log.info("Tier-1 Exact cache HIT: model={} query='{}'", model, truncate(queryText, 40));
            return CacheLookupResult.exact(exact.responseText(), exact.model(), exact.promptTokens(), exact.responseTokens());
        }

        // Tier 2: Semantic Vector Lookup via pgvector
        try {
            float[] queryEmbedding = ollama.embed(new EmbedRequest(embeddingModel, queryText)).embedding();
            Optional<SemanticCacheEntry> match = semanticRepo.findClosestMatch(
                    queryEmbedding, productionProps.similarityThreshold());

            if (match.isPresent()) {
                SemanticCacheEntry entry = match.get();
                log.info("Tier-2 Semantic cache HIT (similarity={}): query='{}' matched='{}'",
                        String.format("%.3f", entry.similarity()),
                        truncate(queryText, 40),
                        truncate(entry.queryText(), 40));
                return CacheLookupResult.semantic(
                        entry.responseText(), entry.model(), entry.similarity(),
                        entry.promptTokens(), entry.responseTokens());
            }
        } catch (Exception e) {
            // Semantic cache lookup failure should never break the primary flow — gracefully fallback to model
            log.warn("Semantic cache lookup failed, proceeding to model: {}", e.getMessage());
        }

        return CacheLookupResult.miss();
    }

    /**
     * Store a new response in both Tier 1 (exact hash) and Tier 2 (pgvector semantic).
     */
    public void put(String queryText, String responseText, String model,
                    Integer promptTokens, Integer responseTokens) {
        if (!productionProps.cacheEnabled()) {
            return;
        }

        // Store Tier 1
        String key = hashKey(model, queryText);
        exactCache.put(key, new ExactEntry(responseText, model, promptTokens, responseTokens, Instant.now()));

        // Store Tier 2
        try {
            float[] embedding = ollama.embed(new EmbedRequest(embeddingModel, queryText)).embedding();
            semanticRepo.save(queryText, responseText, model, embedding, promptTokens, responseTokens);
            log.debug("Stored response in exact and semantic caches for query='{}'", truncate(queryText, 40));
        } catch (Exception e) {
            log.warn("Failed to store semantic cache entry for query: {}", e.getMessage());
        }
    }

    /**
     * Clear both cache tiers.
     */
    public void clear() {
        exactCache.clear();
        semanticRepo.clear();
        log.info("Cleared both exact and semantic response caches");
    }

    /**
     * Get count of entries in the exact cache.
     */
    public int getExactCacheSize() {
        return exactCache.size();
    }

    /**
     * Get count of entries in the semantic cache.
     */
    public long getSemanticCacheSize() {
        return semanticRepo.count();
    }

    private String hashKey(String model, String queryText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((model + "::" + queryText.trim().toLowerCase()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
