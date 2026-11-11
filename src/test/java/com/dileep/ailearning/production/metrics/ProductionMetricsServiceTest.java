package com.dileep.ailearning.production.metrics;

import com.dileep.ailearning.production.dto.ProductionMetricsSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionMetricsServiceTest {

    private ProductionMetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new ProductionMetricsService();
    }

    @Test
    void tracksRequestsHitsAndHitRatioAccurately() {
        metricsService.recordExactCacheHit(100, 50);
        metricsService.recordSemanticCacheHit(100, 50);
        metricsService.recordCacheMiss(120, 60);
        metricsService.recordCacheMiss(110, 40);

        ProductionMetricsSnapshot snapshot = metricsService.getSnapshot();

        assertThat(snapshot.totalRequests()).isEqualTo(4);
        assertThat(snapshot.exactCacheHits()).isEqualTo(1);
        assertThat(snapshot.semanticCacheHits()).isEqualTo(1);
        assertThat(snapshot.cacheMisses()).isEqualTo(2);
        // (1 + 1) / 4 = 50.0%
        assertThat(snapshot.cacheHitRatio()).isEqualTo(50.0);
        assertThat(snapshot.promptTokensSaved()).isEqualTo(200);
        assertThat(snapshot.responseTokensSaved()).isEqualTo(100);
        assertThat(snapshot.promptTokensServed()).isEqualTo(230);
        assertThat(snapshot.responseTokensServed()).isEqualTo(100);
    }

    @Test
    void tracksTtftAndTpotStreamingMetrics() {
        metricsService.recordStreamingMetrics(120L, 25.5);
        metricsService.recordStreamingMetrics(180L, 34.5);

        ProductionMetricsSnapshot snapshot = metricsService.getSnapshot();

        // avg TTFT: (120 + 180) / 2 = 150.0
        assertThat(snapshot.avgTtftMs()).isEqualTo(150.0);
        // avg TPOT: (25.5 + 34.5) / 2 = 30.0
        assertThat(snapshot.avgTpotMs()).isEqualTo(30.0);
    }

    @Test
    void resetsAllCounters() {
        metricsService.recordExactCacheHit(100, 50);
        metricsService.recordStreamingMetrics(100L, 20.0);
        metricsService.recordFallback();

        metricsService.reset();

        ProductionMetricsSnapshot snapshot = metricsService.getSnapshot();
        assertThat(snapshot.totalRequests()).isEqualTo(0);
        assertThat(snapshot.fallbackCount()).isEqualTo(0);
        assertThat(snapshot.avgTtftMs()).isEqualTo(0.0);
    }
}
