package com.dileep.ailearning.invoice;

import com.dileep.ailearning.common.JsonSanitizer;
import com.dileep.ailearning.config.InvoiceExtractionProperties;
import com.dileep.ailearning.invoice.model.Invoice;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.ollama.dto.Message;
import com.dileep.ailearning.ollama.dto.Options;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Module 1 — turn raw invoice text into a validated {@link Invoice} object,
 * reliably.
 *
 * <p>The reliability recipe, all in one place:
 * <ol>
 *   <li><b>Strict schema + few-shot</b> system prompt (see {@link InvoicePromptFactory}).</li>
 *   <li><b>{@code format: "json"}</b> + <b>temperature 0</b> + fixed <b>seed</b>
 *       for valid, reproducible output.</li>
 *   <li><b>Parse</b> the JSON into our record (a first correctness gate).</li>
 *   <li><b>Validate</b> against Bean-Validation rules (the guardrail).</li>
 *   <li><b>Auto-retry</b> once, feeding the model its own bad output + the reason
 *       it failed. If it still fails we reject — never persist garbage.</li>
 * </ol>
 */
@Service
public class InvoiceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceExtractionService.class);

    private final OllamaClient ollama;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final InvoicePromptFactory prompts;
    private final InvoiceExtractionProperties config;

    public InvoiceExtractionService(OllamaClient ollama,
                                    ObjectMapper objectMapper,
                                    Validator validator,
                                    InvoicePromptFactory prompts,
                                    InvoiceExtractionProperties config) {
        this.ollama = ollama;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.prompts = prompts;
        this.config = config;
    }

    public ExtractionResult extract(String rawInvoiceText) {
        if (rawInvoiceText == null || rawInvoiceText.isBlank()) {
            throw new IllegalArgumentException("Invoice text must not be empty.");
        }

        // The conversation grows on each retry (we append the bad answer + a correction).
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(prompts.systemPrompt()));
        messages.add(Message.user(prompts.userPrompt(rawInvoiceText)));

        Options options = Options.forExtraction(config.temperature(), config.seed(), config.numCtx());

        int maxAttempts = config.maxRetries() + 1;
        long startNanos = System.nanoTime();
        String lastRaw = "";
        List<String> lastProblems = List.of("No response received.");

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ChatResponse response = ollama.chat(ChatRequest.json(config.model(), messages, options));
            lastRaw = response.content();
            String json = JsonSanitizer.extractJsonObject(lastRaw);

            try {
                Invoice invoice = objectMapper.readValue(json, Invoice.class);

                Set<ConstraintViolation<Invoice>> violations = validator.validate(invoice);
                if (violations.isEmpty()) {
                    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                    log.info("invoice extracted on attempt {}/{} in {} ms", attempt, maxAttempts, durationMs);
                    return new ExtractionResult(invoice, config.model(), attempt, durationMs,
                            response.promptEvalCount(), response.evalCount());
                }
                lastProblems = describeViolations(violations);
            } catch (JsonProcessingException e) {
                // Valid-looking text that isn't parseable into our schema.
                lastProblems = List.of("Invalid JSON: " + e.getOriginalMessage());
            }

            log.warn("extraction attempt {}/{} rejected: {}", attempt, maxAttempts, lastProblems);

            boolean hasRetryLeft = attempt < maxAttempts;
            if (hasRetryLeft) {
                // Show the model its own bad output, then tell it exactly what to fix.
                messages.add(Message.assistant(lastRaw));
                messages.add(Message.user(prompts.correctionPrompt(String.join("; ", lastProblems))));
            }
        }

        throw new ExtractionFailedException(
                "Model did not return valid, schema-correct JSON after " + maxAttempts + " attempt(s).",
                lastRaw, lastProblems, maxAttempts);
    }

    /** Turn raw constraint violations into "field: message" strings for the retry nudge and the API error. */
    private List<String> describeViolations(Set<ConstraintViolation<Invoice>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .toList();
    }
}
