package com.dileep.ailearning.invoice;

import com.dileep.ailearning.config.InvoiceExtractionProperties;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.ollama.dto.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Module 2 — the image path. We mock Ollama and assert the multimodal wiring:
 * the image is base64-encoded into the user message, the vision model is used,
 * and the exact same parse/validate/auto-retry guarantees from Module 1 apply.
 */
class InvoiceVisionExtractionTest {

    private static final String VALID_JSON = """
            {"invoiceNumber":"INV-1","invoiceDate":"2025-01-01","vendorName":"Foo Ltd",
             "vendorGstin":null,"buyerName":null,"buyerGstin":null,"currency":"INR",
             "lineItems":[{"description":"Widget","hsnCode":null,"quantity":2,
                           "unitPrice":50.00,"taxRate":18,"lineTotal":100.00}],
             "subtotal":100.00,"taxAmount":18.00,"totalAmount":118.00}
            """;

    private final byte[] imageBytes = "pretend-jpeg-bytes".getBytes(StandardCharsets.UTF_8);

    private OllamaClient ollama;
    private InvoiceExtractionService service;

    @BeforeEach
    void setUp() {
        ollama = mock(OllamaClient.class);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        var config = new InvoiceExtractionProperties("test-model", "test-vision-model", 0.0, 42, 8192, 1);
        service = new InvoiceExtractionService(ollama, objectMapper, validator,
                new InvoicePromptFactory(), config);
    }

    @Test
    void base64EncodesImageAndTargetsVisionModel() {
        when(ollama.chat(any())).thenReturn(response(VALID_JSON));

        ExtractionResult result = service.extractFromImage(imageBytes);

        assertThat(result.invoice().invoiceNumber()).isEqualTo("INV-1");
        assertThat(result.model()).isEqualTo("test-vision-model");

        var captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(ollama).chat(captor.capture());
        ChatRequest sent = captor.getValue();

        assertThat(sent.model()).isEqualTo("test-vision-model"); // vision model, not the text one
        assertThat(sent.format()).isEqualTo("json");             // still schema-constrained

        Message userMessage = sent.messages().get(1);
        assertThat(userMessage.role()).isEqualTo("user");
        assertThat(userMessage.images())
                .containsExactly(Base64.getEncoder().encodeToString(imageBytes));
    }

    @Test
    void retriesOnInvalidJson_withoutResendingTheImage() {
        when(ollama.chat(any()))
                .thenReturn(response("not json"))
                .thenReturn(response(VALID_JSON));

        ExtractionResult result = service.extractFromImage(imageBytes);

        assertThat(result.attempts()).isEqualTo(2);
        verify(ollama, times(2)).chat(any());

        // After the retry the conversation is: system, user(+image), assistant(bad), user(correction).
        var captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(ollama, times(2)).chat(captor.capture());
        var finalMessages = captor.getValue().messages();
        assertThat(finalMessages).hasSize(4);
        assertThat(finalMessages.get(1).images()).isNotEmpty();   // original turn carries the image
        assertThat(finalMessages.get(3).images()).isNull();       // correction is text-only (no re-upload)
    }

    @Test
    void rejectsEmptyImage() {
        assertThatThrownBy(() -> service.extractFromImage(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ChatResponse response(String content) {
        return new ChatResponse("test-vision-model", new Message("assistant", content), true, 1_000_000L, 100, 50);
    }
}
