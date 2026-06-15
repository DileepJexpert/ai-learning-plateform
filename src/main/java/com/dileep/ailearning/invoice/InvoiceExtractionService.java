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
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Module 1 &amp; 2 — turn an invoice (raw text <i>or</i> a photo) into a validated
 * {@link Invoice} object, reliably.
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
 *
 * <p>The text path (Module 1) and the image path (Module 2) differ only in the
 * <em>initial user message</em> and which model they target — the entire
 * parse/validate/retry loop is shared (see {@link #run}). That is the payoff of
 * mapping every input onto one fixed domain object.
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

    /** Module 1: extract from raw invoice text using the configured text model. */
    public ExtractionResult extract(String rawInvoiceText) {
        if (rawInvoiceText == null || rawInvoiceText.isBlank()) {
            throw new IllegalArgumentException("Invoice text must not be empty.");
        }
        return run(Message.user(prompts.userPrompt(rawInvoiceText)), config.model());
    }

    /**
     * Module 2: extract from an invoice <b>image</b> (e.g. a phone photo) using the
     * configured vision model.
     *
     * <p>The image bytes are <b>base64-encoded</b> so they can ride inside the JSON
     * request body — that is how binary travels over Ollama's text-based API.
     */
    public ExtractionResult extractFromImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Invoice image must not be empty.");
        }
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        Message imageMessage = Message.userWithImages(prompts.visionUserPrompt(), List.of(base64));
        return run(imageMessage, config.visionModel());
    }

    /**
     * The shared loop: send, parse, validate, and auto-retry. The only inputs that
     * vary by modality are the first user message and the model.
     */
    private ExtractionResult run(Message initialUserMessage, String model) {
        // The conversation grows on each retry (we append the bad answer + a correction).
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(prompts.systemPrompt()));
        messages.add(initialUserMessage);

        Options options = Options.forExtraction(config.temperature(), config.seed(), config.numCtx());

        int maxAttempts = config.maxRetries() + 1;
        long startNanos = System.nanoTime();
        String lastRaw = "";
        List<String> lastProblems = List.of("No response received.");

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ChatResponse response = ollama.chat(ChatRequest.json(model, messages, options));
            lastRaw = response.content();
            String json = JsonSanitizer.extractJsonObject(lastRaw);

            try {
                Invoice invoice = objectMapper.readValue(json, Invoice.class);

                Set<ConstraintViolation<Invoice>> violations = validator.validate(invoice);
                if (violations.isEmpty()) {
                    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                    log.info("invoice extracted via {} on attempt {}/{} in {} ms",
                            model, attempt, maxAttempts, durationMs);
                    return new ExtractionResult(invoice, model, attempt, durationMs,
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
                // The correction is text-only — no need to resend the image; it is
                // already in the conversation context.
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
