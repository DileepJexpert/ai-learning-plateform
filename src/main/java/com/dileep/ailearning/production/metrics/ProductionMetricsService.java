package com.dileep.ailearning.production.metrics;

import com.dileep.ailearning.production.dto.ProductionMetricsSnapshot;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks FinOps and SLA metrics for LLM calls (Module 7).
 *
 * <p>In enterprise banking AI, measuring TTFT (Time To First Token), TPOT (Time Per Output Token),
 * and cache hit rates is mandatory to meet SLA/SLO agreements and monitor token budgets.
 */
@Service
public class ProductionMetricsService {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong exactCacheHits = new AtomicLong(0);
    private final AtomicLong semanticCacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong fallbackCount = new AtomicLong(0);

    private final AtomicLong promptTokensServed = new AtomicLong(0);
    private final AtomicLong responseTokensServed = new AtomicLong(0);
    private final AtomicLong promptTokensSaved = new AtomicLong(0);
    private final AtomicLong responseTokensSaved = new AtomicLong(0);

    private final AtomicLong totalTtftMs = new AtomicLong(0);
    private final AtomicLong streamingSessionsCount = new AtomicLong(0);
    private final AtomicLong totalTpotMsTimes100 = new AtomicLong(0);

    public void recordExactCacheHit(int promptTokens, int responseTokens) {
        totalRequests.incrementAndGet();
        exactCacheHits.incrementAndGet();
        promptTokensSaved.addAndGet(promptTokens);
        responseTokensSaved.addAndGet(responseTokens);
    }

    public void recordSemanticCacheHit(int promptTokens, int responseTokens) {
        totalRequests.incrementAndGet();
        semanticCacheHits.incrementAndGet();
        promptTokensSaved.addAndGet(promptTokens);
        responseTokensSaved.addAndGet(responseTokens);
    }

    public void recordCacheMiss(int promptTokens, int responseTokens) {
        totalRequests.incrementAndGet();
        cacheMisses.incrementAndGet();
        promptTokensServed.addAndGet(promptTokens);
        responseTokensServed.addAndGet(responseTokens);
    }

    public void recordFallback() {
        fallbackCount.incrementAndGet();
    }

    public void recordStreamingMetrics(long ttftMs, double tpotMs) {
        streamingSessionsCount.incrementAndGet();
        totalTtftMs.addAndGet(ttftMs);
        totalTpotMsTimes100.addAndGet(Math.round(tpotMs * 100));
    }

    public ProductionMetricsSnapshot getSnapshot() {
        long total = totalRequests.get();
        long exact = exactCacheHits.get();
        long semantic = semanticCacheHits.get();
        long hits = exact + semantic;
        double hitRatio = total == 0 ? 0.0 : ((double) hits / total);

        long streamCount = streamingSessionsCount.get();
        double avgTtft = streamCount == 0 ? 0.0 : ((double) totalTtftMs.get() / streamCount);
        double avgTpot = streamCount == 0 ? 0.0 : ((double) totalTpotMsTimes100.get() / (streamCount * 100.0));

        return new ProductionMetricsSnapshot(
                total,
                exact,
                semantic,
                cacheMisses.get(),
                Math.round(hitRatio * 1000.0) / 10.0, // percentage e.g. 75.5%
                fallbackCount.get(),
                promptTokensServed.get(),
                responseTokensServed.get(),
                promptTokensSaved.get(),
                responseTokensSaved.get(),
                Math.round(avgTtft * 10.0) / 10.0,
                Math.round(avgTpot * 10.0) / 10.0
        );
    }

    public void reset() {
        totalRequests.set(0);
        exactCacheHits.set(0);
        semanticCacheHits.set(0);
        cacheMisses.set(0);
        fallbackCount.set(0);
        promptTokensServed.set(0);
        responseTokensServed.set(0);
        promptTokensSaved.set(0);
        responseTokensSaved.set(0);
        totalTtftMs.set(0);
        streamingSessionsCount.set(0);
        totalTpotMsTimes100.set(0);
    }
}
