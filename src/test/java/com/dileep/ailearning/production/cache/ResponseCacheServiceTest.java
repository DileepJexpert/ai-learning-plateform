package com.dileep.ailearning.production.cache;

import com.dileep.ailearning.config.EmbeddingProperties;
import com.dileep.ailearning.config.ProductionProperties;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.EmbedRequest;
import com.dileep.ailearning.ollama.dto.EmbedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ResponseCacheServiceTest {

    private final SemanticCacheRepository semanticRepo = mock(SemanticCacheRepository.class);
    private final OllamaClient ollama = mock(OllamaClient.class);
    private final EmbeddingProperties embeddingProps = new EmbeddingProperties("nomic-embed-text", 768, 500, 100);
    private final ProductionProperties productionProps = new ProductionProperties(true, 0.90, true, "llama3.2:3b", Duration.ofSeconds(60));

    private ResponseCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new ResponseCacheService(semanticRepo, ollama, embeddingProps, productionProps);
    }

    @Test
    void exactCacheHitReturnsInstantlyWithoutEmbedding() {
        // Prime exact cache
        cacheService.put("What is an embedding?", "An embedding is a vector.", "qwen2.5-coder:7b", 10, 20);

        ResponseCacheService.CacheLookupResult result = cacheService.get("What is an embedding?", "qwen2.5-coder:7b");

        assertThat(result.hit()).isTrue();
        assertThat(result.cacheType()).isEqualTo(ResponseCacheService.CacheType.EXACT);
        assertThat(result.responseText()).isEqualTo("An embedding is a vector.");
        assertThat(result.similarity()).isEqualTo(1.0);

        // Verify embed was only called during put, not during get lookup
        verify(ollama, times(1)).embed(any(EmbedRequest.class));
        verify(semanticRepo, never()).findClosestMatch(any(), anyDouble());
    }

    @Test
    void semanticCacheHitReturnsWhenExactMissesAndSimilarityExceedsThreshold() {
        float[] vector = new float[]{0.1f, 0.2f};
        when(ollama.embed(any(EmbedRequest.class))).thenReturn(new EmbedResponse(vector));

        SemanticCacheEntry entry = new SemanticCacheEntry(
                1L,
                "What is vector embedding?",
                "Embeddings map text to vectors.",
                "qwen2.5-coder:7b",
                0.94,
                15,
                25,
                Instant.now()
        );
        when(semanticRepo.findClosestMatch(eq(vector), eq(0.90))).thenReturn(Optional.of(entry));

        // Exact miss since query is slightly different
        ResponseCacheService.CacheLookupResult result = cacheService.get("Explain vector embedding", "qwen2.5-coder:7b");

        assertThat(result.hit()).isTrue();
        assertThat(result.cacheType()).isEqualTo(ResponseCacheService.CacheType.SEMANTIC);
        assertThat(result.similarity()).isEqualTo(0.94);
        assertThat(result.responseText()).isEqualTo("Embeddings map text to vectors.");
    }

    @Test
    void returnsMissWhenBothExactAndSemanticMiss() {
        float[] vector = new float[]{0.1f, 0.2f};
        when(ollama.embed(any(EmbedRequest.class))).thenReturn(new EmbedResponse(vector));
        when(semanticRepo.findClosestMatch(eq(vector), eq(0.90))).thenReturn(Optional.empty());

        ResponseCacheService.CacheLookupResult result = cacheService.get("Completely new question", "qwen2.5-coder:7b");

        assertThat(result.hit()).isFalse();
        assertThat(result.cacheType()).isEqualTo(ResponseCacheService.CacheType.NONE);
    }

    @Test
    void clearPurgesBothTiers() {
        cacheService.put("Prompt A", "Answer A", "qwen2.5-coder:7b", 5, 5);
        assertThat(cacheService.getExactCacheSize()).isEqualTo(1);

        cacheService.clear();

        assertThat(cacheService.getExactCacheSize()).isEqualTo(0);
        verify(semanticRepo).clear();
    }
}
