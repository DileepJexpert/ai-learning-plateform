package com.dileep.ailearning.production.streaming;

import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.production.dto.StreamSummary;
import com.dileep.ailearning.production.dto.StreamTokenEvent;
import com.dileep.ailearning.production.metrics.ProductionMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles real-time token streaming over Server-Sent Events (SSE) using Java 21 Virtual Threads (Module 7).
 *
 * <p>Why Virtual Threads?
 * Standard reactive frameworks (WebFlux) require completely rewriting code to non-blocking monads.
 * With Java 21 Project Loom virtual threads, long-running I/O connections (like streaming 500-token LLM outputs
 * over several seconds) execute on cheap virtual threads without exhausting standard OS threads or requiring WebFlux.
 */
@Service
public class StreamingChatService {

    private static final Logger log = LoggerFactory.getLogger(StreamingChatService.class);

    private final OllamaClient ollama;
    private final ProductionMetricsService metricsService;

    public StreamingChatService(OllamaClient ollama, ProductionMetricsService metricsService) {
        this.ollama = ollama;
        this.metricsService = metricsService;
    }

    /**
     * Executes the streaming chat on a dedicated Java 21 Virtual Thread, pushing tokens to {@code emitter}.
     */
    public void streamToEmitter(ChatRequest request, SseEmitter emitter) {
        Thread.ofVirtual().name("llm-stream-worker").start(() -> {
            long startNanos = System.nanoTime();
            AtomicBoolean firstTokenSeen = new AtomicBoolean(false);
            AtomicLong ttftMs = new AtomicLong(0);
            AtomicInteger tokenCount = new AtomicInteger(0);

            try {
                ollama.streamChat(request, chunk -> {
                    try {
                        String content = chunk.content();
                        if (content != null && !content.isEmpty()) {
                            if (firstTokenSeen.compareAndSet(false, true)) {
                                long ttft = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                                ttftMs.set(ttft);
                                log.debug("First token received for stream: ttftMs={}", ttft);
                            }
                            tokenCount.incrementAndGet();
                            long currentWallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

                            emitter.send(SseEmitter.event()
                                    .name("token")
                                    .data(new StreamTokenEvent(content, false, ttftMs.get(), null, null, currentWallMs)));
                        }

                        if (chunk.done()) {
                            long totalWallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                            int finalTokens = chunk.evalCount() != null ? chunk.evalCount() : tokenCount.get();
                            long activeGenerationMs = Math.max(1, totalWallMs - ttftMs.get());

                            double tpotMs = (double) activeGenerationMs / Math.max(1, finalTokens);
                            double tokensPerSec = (double) finalTokens / (activeGenerationMs / 1000.0);

                            metricsService.recordStreamingMetrics(ttftMs.get(), tpotMs);

                            StreamSummary summary = new StreamSummary(
                                    chunk.model() != null ? chunk.model() : request.model(),
                                    ttftMs.get(),
                                    Math.round(tpotMs * 100.0) / 100.0,
                                    Math.round(tokensPerSec * 10.0) / 10.0,
                                    finalTokens,
                                    totalWallMs
                            );

                            emitter.send(SseEmitter.event().name("done").data(summary));
                            emitter.complete();
                        }
                    } catch (IOException e) {
                        log.warn("Client disconnected during streaming: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                });
            } catch (Exception e) {
                log.error("Error during streaming chat: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("Streaming error: " + e.getMessage()));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        });
    }
}
