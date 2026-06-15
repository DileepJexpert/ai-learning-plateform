package com.dileep.ailearning.ollama;

import com.dileep.ailearning.config.OllamaProperties;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Module 0 — the one place that knows how to talk to Ollama.
 *
 * <p>A thin, typed wrapper around {@code POST /api/chat}. Everything else in the
 * project goes through here, which means cross-cutting concerns (logging token
 * usage, translating transport errors) live in exactly one spot.
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public OllamaClient(RestClient ollamaRestClient, OllamaProperties properties) {
        this.restClient = ollamaRestClient;
        this.baseUrl = properties.baseUrl();
    }

    /**
     * Send a chat request to Ollama and return the full (non-streaming) response.
     *
     * @throws OllamaException if Ollama is unreachable or returns a non-2xx status
     */
    public ChatResponse chat(ChatRequest request) {
        long startNanos = System.nanoTime();
        try {
            ChatResponse response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String body = readBody(res.getBody());
                        throw new OllamaException(
                                "Ollama returned " + res.getStatusCode() + " for /api/chat: " + body);
                    })
                    .body(ChatResponse.class);

            logUsage(request, response, startNanos);
            return response;
        } catch (ResourceAccessException e) {
            // Connection refused / timeout: the model backend itself is the problem.
            throw new OllamaException(
                    "Could not reach Ollama at " + baseUrl
                            + " — is it running? Start it with `ollama serve`. Cause: " + e.getMessage(),
                    e);
        }
    }

    private void logUsage(ChatRequest request, ChatResponse response, long startNanos) {
        long wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        // prompt_eval_count = input tokens, eval_count = output tokens.
        // Logging these per call is the seed of cost/latency accounting (Module 7).
        log.info("ollama chat: model={} format={} promptTokens={} responseTokens={} wallMs={}",
                request.model(),
                request.format() == null ? "text" : request.format(),
                response.promptEvalCount(),
                response.evalCount(),
                wallMs);
    }

    private static String readBody(java.io.InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<unreadable error body: " + e.getMessage() + ">";
        }
    }
}
