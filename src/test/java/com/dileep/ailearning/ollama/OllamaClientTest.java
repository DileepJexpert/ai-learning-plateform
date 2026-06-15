package com.dileep.ailearning.ollama;

import com.dileep.ailearning.config.OllamaProperties;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.ollama.dto.Message;
import com.dileep.ailearning.ollama.dto.Options;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies the Module 0 HTTP layer — request shape and response parsing — using a
 * mock server, so no real Ollama is required.
 */
class OllamaClientTest {

    private static final String BASE_URL = "http://ollama.test";

    private OllamaClient newClient(MockServerHolder holder) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        holder.server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        var props = new OllamaProperties(BASE_URL, "qwen2.5-coder:7b",
                Duration.ofSeconds(5), Duration.ofSeconds(5));
        return new OllamaClient(restClient, props);
    }

    @Test
    void postsToChatEndpointAndParsesResponse() {
        var holder = new MockServerHolder();
        OllamaClient client = newClient(holder);

        String responseBody = """
                {"model":"qwen2.5-coder:7b",
                 "message":{"role":"assistant","content":"{\\"ok\\":true}"},
                 "done":true,"prompt_eval_count":42,"eval_count":7}
                """;

        holder.server.expect(requestTo(BASE_URL + "/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("qwen2.5-coder:7b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.format").value("json"))
                .andExpect(jsonPath("$.options.temperature").value(0.0))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        ChatResponse response = client.chat(ChatRequest.json(
                "qwen2.5-coder:7b",
                List.of(Message.user("hi")),
                Options.forExtraction(0.0, 42, 8192)));

        assertThat(response.content()).isEqualTo("{\"ok\":true}");
        assertThat(response.promptEvalCount()).isEqualTo(42);
        assertThat(response.evalCount()).isEqualTo(7);
        holder.server.verify();
    }

    @Test
    void wrapsHttpErrorAsOllamaException() {
        var holder = new MockServerHolder();
        OllamaClient client = newClient(holder);

        holder.server.expect(requestTo(BASE_URL + "/api/chat"))
                .andRespond(withServerError().body("model not found"));

        assertThatThrownBy(() -> client.chat(
                ChatRequest.text("missing-model", List.of(Message.user("hi")), null)))
                .isInstanceOf(OllamaException.class)
                .hasMessageContaining("model not found");
    }

    /** Tiny holder so the helper can hand back the configured mock server. */
    private static final class MockServerHolder {
        MockRestServiceServer server;
    }
}
