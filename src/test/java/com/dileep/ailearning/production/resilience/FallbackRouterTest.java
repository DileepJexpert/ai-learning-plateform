package com.dileep.ailearning.production.resilience;

import com.dileep.ailearning.config.ProductionProperties;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.OllamaException;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.ollama.dto.Message;
import com.dileep.ailearning.production.metrics.ProductionMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FallbackRouterTest {

    private final OllamaClient ollama = mock(OllamaClient.class);
    private final ProductionMetricsService metricsService = mock(ProductionMetricsService.class);
    private final ProductionProperties productionProps = new ProductionProperties(
            true, 0.90, true, "llama3.2:3b", Duration.ofSeconds(60));

    private FallbackRouter router;

    @BeforeEach
    void setUp() {
        router = new FallbackRouter(ollama, productionProps, metricsService);
    }

    @Test
    void executesPrimaryModelSuccessfullyWithoutFallback() {
        ChatRequest request = ChatRequest.text("qwen2.5-coder:7b", List.of(Message.user("Hello")), null);
        ChatResponse response = new ChatResponse("qwen2.5-coder:7b", Message.assistant("Hi!"), true, 500L, 10, 5);
        when(ollama.chat(request)).thenReturn(response);

        FallbackRouter.ExecutionResult result = router.execute(request);

        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.executedModel()).isEqualTo("qwen2.5-coder:7b");
        assertThat(result.response().content()).isEqualTo("Hi!");
        verify(metricsService, never()).recordFallback();
    }

    @Test
    void routesToFallbackModelWhenPrimaryFails() {
        ChatRequest request = ChatRequest.text("qwen2.5-coder:7b", List.of(Message.user("Hello")), null);
        when(ollama.chat(argThat(r -> r.model().equals("qwen2.5-coder:7b"))))
                .thenThrow(new OllamaException("Connection timed out"));

        ChatResponse fallbackResponse = new ChatResponse("llama3.2:3b", Message.assistant("Hi from fallback!"), true, 200L, 10, 5);
        when(ollama.chat(argThat(r -> r.model().equals("llama3.2:3b"))))
                .thenReturn(fallbackResponse);

        FallbackRouter.ExecutionResult result = router.execute(request);

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.executedModel()).isEqualTo("llama3.2:3b");
        assertThat(result.response().content()).isEqualTo("Hi from fallback!");
        assertThat(result.failureReason()).contains("Connection timed out");
        verify(metricsService, times(1)).recordFallback();
    }

    @Test
    void throwsExceptionWhenBothPrimaryAndFallbackFail() {
        ChatRequest request = ChatRequest.text("qwen2.5-coder:7b", List.of(Message.user("Hello")), null);
        when(ollama.chat(any()))
                .thenThrow(new OllamaException("Backend unavailable"));

        assertThatThrownBy(() -> router.execute(request))
                .isInstanceOf(OllamaException.class)
                .hasMessageContaining("Inference failed on both primary");
    }
}
