package com.dileep.ailearning.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module 4 HTTP surface: ask a question, get a grounded answer with citations.
 *
 * <p>Ingest documents first via the Module 3 endpoint
 * ({@code POST /api/embeddings/ingest}), then:
 *
 * <pre>
 * curl -s localhost:8080/api/rag/ask \
 *   -H 'Content-Type: application/json' \
 *   -d '{"question":"What is the GST rate for electronic goods?"}'
 * </pre>
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public RagAnswer ask(@RequestBody AskRequest request) {
        return ragService.answer(request.question(), request.topK());
    }

    /** {@code topK} is optional — defaults to the configured value when null. */
    public record AskRequest(@NotBlank String question, @Min(1) @Max(20) Integer topK) {
    }
}
