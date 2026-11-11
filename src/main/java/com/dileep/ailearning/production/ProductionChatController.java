package com.dileep.ailearning.production;

import com.dileep.ailearning.config.OllamaProperties;
import com.dileep.ailearning.config.ProductionProperties;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.Message;
import com.dileep.ailearning.ollama.dto.Options;
import com.dileep.ailearning.production.cache.ResponseCacheService;
import com.dileep.ailearning.production.dto.ProductionChatRequest;
import com.dileep.ailearning.production.dto.ProductionChatResponse;
import com.dileep.ailearning.production.dto.ProductionMetricsSnapshot;
import com.dileep.ailearning.production.metrics.ProductionMetricsService;
import com.dileep.ailearning.production.resilience.FallbackRouter;
import com.dileep.ailearning.production.streaming.StreamingChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Module 7: Production-grade REST controller.
 *
 * <p>Provides:
 * <ul>
 *   <li><b>Cached & Resilient Inference:</b> Exact SHA-256 + pgvector Semantic Caching with automatic Fallback Routing.</li>
 *   <li><b>Streaming Token Delivery:</b> Real-time SSE on Java 21 Project Loom Virtual Threads with TTFT and TPOT tracking.</li>
 *   <li><b>FinOps & LLMOps Observability:</b> Token accounting, latency metrics, and cache hit ratios.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/production")
public class ProductionChatController {

    private final OllamaProperties ollamaProps;
    private final ProductionProperties productionProps;
    private final ResponseCacheService cacheService;
    private final FallbackRouter fallbackRouter;
    private final StreamingChatService streamingService;
    private final ProductionMetricsService metricsService;

    public ProductionChatController(OllamaProperties ollamaProps,
                                    ProductionProperties productionProps,
                                    ResponseCacheService cacheService,
                                    FallbackRouter fallbackRouter,
                                    StreamingChatService streamingService,
                                    ProductionMetricsService metricsService) {
        this.ollamaProps = ollamaProps;
        this.productionProps = productionProps;
        this.cacheService = cacheService;
        this.fallbackRouter = fallbackRouter;
        this.streamingService = streamingService;
        this.metricsService = metricsService;
    }

    /**
     * Resilient, cached chat completion endpoint.
     */
    @PostMapping("/chat")
    public ProductionChatResponse chat(@Valid @RequestBody ProductionChatRequest request) {
        long startNanos = System.nanoTime();
        String targetModel = ollamaProps.model();
        boolean bypassCache = Boolean.TRUE.equals(request.bypassCache());

        // 1. Check Caching Tiers (Exact SHA-256 then Semantic pgvector)
        if (!bypassCache) {
            ResponseCacheService.CacheLookupResult cacheResult = cacheService.get(request.prompt(), targetModel);
            if (cacheResult.hit()) {
                long wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                if (cacheResult.cacheType() == ResponseCacheService.CacheType.EXACT) {
                    metricsService.recordExactCacheHit(
                            cacheResult.promptTokens() != null ? cacheResult.promptTokens() : 0,
                            cacheResult.responseTokens() != null ? cacheResult.responseTokens() : 0);
                } else {
                    metricsService.recordSemanticCacheHit(
                            cacheResult.promptTokens() != null ? cacheResult.promptTokens() : 0,
                            cacheResult.responseTokens() != null ? cacheResult.responseTokens() : 0);
                }

                return new ProductionChatResponse(
                        cacheResult.responseText(),
                        cacheResult.model(),
                        true,
                        cacheResult.cacheType().name(),
                        cacheResult.similarity(),
                        false,
                        cacheResult.promptTokens(),
                        cacheResult.responseTokens(),
                        wallMs
                );
            }
        }

        // 2. Cache Miss: Build Messages
        List<Message> messages = new ArrayList<>();
        if (request.system() != null && !request.system().isBlank()) {
            messages.add(Message.system(request.system()));
        }
        messages.add(Message.user(request.prompt()));

        Options options = request.temperature() != null ? new Options(request.temperature(), null, null) : null;
        ChatRequest chatRequest = ChatRequest.text(targetModel, messages, options);

        // 3. Execute with Fallback Routing
        FallbackRouter.ExecutionResult execution = fallbackRouter.execute(chatRequest);
        var chatResponse = execution.response();
        long wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        // 4. Update Caches & Metrics
        if (!bypassCache) {
            cacheService.put(
                    request.prompt(),
                    chatResponse.content(),
                    execution.executedModel(),
                    chatResponse.promptEvalCount(),
                    chatResponse.evalCount()
            );
        }

        metricsService.recordCacheMiss(
                chatResponse.promptEvalCount() != null ? chatResponse.promptEvalCount() : 0,
                chatResponse.evalCount() != null ? chatResponse.evalCount() : 0
        );

        return new ProductionChatResponse(
                chatResponse.content(),
                execution.executedModel(),
                false,
                ResponseCacheService.CacheType.NONE.name(),
                0.0,
                execution.fallbackUsed(),
                chatResponse.promptEvalCount(),
                chatResponse.evalCount(),
                wallMs
        );
    }

    /**
     * Real-time SSE streaming chat endpoint powered by Java 21 Virtual Threads.
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGet(@RequestParam String prompt,
                                @RequestParam(required = false) String system) {
        return createStreamEmitter(prompt, system);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPost(@Valid @RequestBody ProductionChatRequest request) {
        return createStreamEmitter(request.prompt(), request.system());
    }

    /**
     * Retrieve FinOps and SLA metrics.
     */
    @GetMapping("/metrics")
    public ProductionMetricsSnapshot getMetrics() {
        return metricsService.getSnapshot();
    }

    @PostMapping("/metrics/reset")
    public void resetMetrics() {
        metricsService.reset();
    }

    /**
     * Inspect cache sizes.
     */
    @GetMapping("/cache/stats")
    public Map<String, Object> getCacheStats() {
        return Map.of(
                "exactCacheSize", cacheService.getExactCacheSize(),
                "semanticCacheSize", cacheService.getSemanticCacheSize(),
                "cacheEnabled", productionProps.cacheEnabled(),
                "similarityThreshold", productionProps.similarityThreshold()
        );
    }

    /**
     * Purge both cache tiers.
     */
    @DeleteMapping("/cache")
    public Map<String, String> clearCache() {
        cacheService.clear();
        return Map.of("status", "cache cleared");
    }

    private SseEmitter createStreamEmitter(String prompt, String system) {
        SseEmitter emitter = new SseEmitter(productionProps.streamingTimeout().toMillis());

        List<Message> messages = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            messages.add(Message.system(system));
        }
        messages.add(Message.user(prompt));

        ChatRequest chatRequest = ChatRequest.streaming(ollamaProps.model(), messages, null);
        streamingService.streamToEmitter(chatRequest, emitter);

        return emitter;
    }
}
