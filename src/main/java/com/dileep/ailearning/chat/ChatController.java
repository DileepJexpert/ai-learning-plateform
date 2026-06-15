package com.dileep.ailearning.chat;

import com.dileep.ailearning.config.OllamaProperties;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.ollama.dto.Message;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Module 0 — the simplest possible thing: send a prompt to the local model and
 * get text back. Proves the Spring Boot ⇄ Ollama round-trip works end to end.
 *
 * <pre>
 * curl -s localhost:8080/api/chat \
 *   -H 'Content-Type: application/json' \
 *   -d '{"prompt":"In one sentence, what is an embedding?"}'
 * </pre>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final OllamaClient ollama;
    private final String defaultModel;

    public ChatController(OllamaClient ollama, OllamaProperties properties) {
        this.ollama = ollama;
        this.defaultModel = properties.model();
    }

    @PostMapping
    public ChatReply chat(@RequestBody ChatPrompt request) {
        List<Message> messages = new ArrayList<>();
        if (request.system() != null && !request.system().isBlank()) {
            messages.add(Message.system(request.system()));
        }
        messages.add(Message.user(request.prompt()));

        // No options => model defaults. Plain text (no format:json) for a basic chat.
        ChatResponse response = ollama.chat(ChatRequest.text(defaultModel, messages, null));

        return new ChatReply(response.content(), response.model(),
                response.promptEvalCount(), response.evalCount());
    }

    /** Optional {@code system} steers the model; {@code prompt} is the question. */
    public record ChatPrompt(String system, @NotBlank String prompt) {
    }

    public record ChatReply(String reply, String model, Integer promptTokens, Integer responseTokens) {
    }
}
