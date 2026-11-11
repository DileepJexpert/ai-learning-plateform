package com.dileep.ailearning.production.resilience;

import com.dileep.ailearning.config.ProductionProperties;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.OllamaException;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.production.metrics.ProductionMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback routing mechanism (Module 7).
 *
 * <p>In production enterprise banking environments, LLMs may degrade under load,
 * hit rate limits, or suffer OOM crashes. The FallbackRouter ensures graceful failover
 * to a lightweight SLM (e.g. {@code llama3.2:3b}) rather than returning an outright 500 error.
 */
@Component
public class FallbackRouter {

    private static final Logger log = LoggerFactory.getLogger(FallbackRouter.class);

    public record ExecutionResult(
            ChatResponse response,
            boolean fallbackUsed,
            String executedModel,
            String failureReason
    ) {
    }

    private final OllamaClient ollama;
    private final ProductionProperties productionProps;
    private final ProductionMetricsService metricsService;

    public FallbackRouter(OllamaClient ollama,
                          ProductionProperties productionProps,
                          ProductionMetricsService metricsService) {
        this.ollama = ollama;
        this.productionProps = productionProps;
        this.metricsService = metricsService;
    }

    /**
     * Executes the chat request with fallback resilience.
     */
    public ExecutionResult execute(ChatRequest request) {
        String primaryModel = request.model();
        try {
            ChatResponse response = ollama.chat(request);
            return new ExecutionResult(response, false, primaryModel, null);
        } catch (Exception primaryEx) {
            if (!productionProps.fallbackEnabled() || primaryModel.equals(productionProps.fallbackModel())) {
                throw primaryEx;
            }

            String fallbackModel = productionProps.fallbackModel();
            log.warn("Primary model '{}' execution failed ({}). Failing over to fallback model '{}'",
                    primaryModel, primaryEx.getMessage(), fallbackModel);

            metricsService.recordFallback();

            try {
                ChatRequest fallbackRequest = new ChatRequest(
                        fallbackModel,
                        request.messages(),
                        request.stream(),
                        request.format(),
                        request.options(),
                        request.tools()
                );
                ChatResponse fallbackResponse = ollama.chat(fallbackRequest);
                return new ExecutionResult(fallbackResponse, true, fallbackModel, primaryEx.getMessage());
            } catch (Exception fallbackEx) {
                log.error("Both primary ('{}') and fallback ('{}') models failed. Cause: {}",
                        primaryModel, fallbackModel, fallbackEx.getMessage());
                throw new OllamaException("Inference failed on both primary ('" + primaryModel
                        + "') and fallback ('" + fallbackModel + "'): " + fallbackEx.getMessage(), fallbackEx);
            }
        }
    }
}
