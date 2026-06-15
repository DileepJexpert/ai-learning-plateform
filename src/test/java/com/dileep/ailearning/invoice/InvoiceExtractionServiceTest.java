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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The most valuable tests in the project: they pin down the Module 1 reliability
 * behaviour (parse + validate + auto-retry) without ever touching a real model.
 * Ollama is mocked, so these run anywhere, fast and deterministically.
 */
class InvoiceExtractionServiceTest {

    private static final String VALID_JSON = """
            {"invoiceNumber":"INV-1","invoiceDate":"2025-01-01","vendorName":"Foo Ltd",
             "vendorGstin":null,"buyerName":null,"buyerGstin":null,"currency":"INR",
             "lineItems":[{"description":"Widget","hsnCode":null,"quantity":2,
                           "unitPrice":50.00,"taxRate":18,"lineTotal":100.00}],
             "subtotal":100.00,"taxAmount":18.00,"totalAmount":118.00}
            """;

    // Parses fine, but violates the schema: an invoice must have >= 1 line item.
    private static final String EMPTY_LINE_ITEMS_JSON = """
            {"invoiceNumber":"INV-1","lineItems":[],"currency":"INR"}
            """;

    private OllamaClient ollama;
    private InvoiceExtractionService service;

    @BeforeEach
    void setUp() {
        ollama = mock(OllamaClient.class);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        var config = new InvoiceExtractionProperties("test-model", 0.0, 42, 8192, 1);
        service = new InvoiceExtractionService(ollama, objectMapper, validator,
                new InvoicePromptFactory(), config);
    }

    @Test
    void returnsParsedInvoiceOnFirstAttempt() {
        when(ollama.chat(any())).thenReturn(response(VALID_JSON));

        ExtractionResult result = service.extract("any invoice text");

        assertThat(result.attempts()).isEqualTo(1);
        assertThat(result.model()).isEqualTo("test-model");
        assertThat(result.invoice().invoiceNumber()).isEqualTo("INV-1");
        assertThat(result.invoice().lineItems()).hasSize(1);
        assertThat(result.invoice().totalAmount()).isEqualByComparingTo("118.00");
        verify(ollama, times(1)).chat(any());
    }

    @Test
    void retriesOnceThenSucceeds_whenFirstReplyIsNotJson() {
        when(ollama.chat(any()))
                .thenReturn(response("Sure! Here is the data you asked for."))
                .thenReturn(response(VALID_JSON));

        ExtractionResult result = service.extract("any invoice text");

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.invoice().invoiceNumber()).isEqualTo("INV-1");
        verify(ollama, times(2)).chat(any());
    }

    @Test
    void retriesOnSchemaViolation_thenSucceeds() {
        // First reply is valid JSON but breaks a guardrail (no line items); second is good.
        when(ollama.chat(any()))
                .thenReturn(response(EMPTY_LINE_ITEMS_JSON))
                .thenReturn(response(VALID_JSON));

        ExtractionResult result = service.extract("any invoice text");

        assertThat(result.attempts()).isEqualTo(2);
        verify(ollama, times(2)).chat(any());
    }

    @Test
    void rejectsAfterExhaustingRetries() {
        when(ollama.chat(any())).thenReturn(response("still not json"));

        assertThatThrownBy(() -> service.extract("any invoice text"))
                .isInstanceOf(ExtractionFailedException.class)
                .satisfies(ex -> {
                    var failure = (ExtractionFailedException) ex;
                    assertThat(failure.getAttempts()).isEqualTo(2); // maxRetries(1) + 1
                    assertThat(failure.getProblems()).isNotEmpty();
                    assertThat(failure.getRawModelOutput()).contains("still not json");
                });

        verify(ollama, times(2)).chat(any()); // exactly maxRetries + 1 calls, no more
    }

    @Test
    void usesJsonFormatAndConfiguredModel() {
        when(ollama.chat(any())).thenReturn(response(VALID_JSON));

        service.extract("any invoice text");

        var captor = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        verify(ollama).chat(captor.capture());
        ChatRequest sent = captor.getValue();
        assertThat(sent.format()).isEqualTo("json");   // format: json is set
        assertThat(sent.model()).isEqualTo("test-model");
        assertThat(sent.options().temperature()).isZero(); // deterministic extraction
        assertThat(sent.messages().get(0).role()).isEqualTo("system");
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> service.extract("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ChatResponse response(String content) {
        return new ChatResponse("test-model", new Message("assistant", content), true, 1_000_000L, 100, 50);
    }
}
