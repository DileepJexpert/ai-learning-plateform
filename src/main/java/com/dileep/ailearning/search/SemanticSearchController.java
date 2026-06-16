package com.dileep.ailearning.search;

import com.dileep.ailearning.embedding.ChunkRepository;
import com.dileep.ailearning.embedding.EmbeddingService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Module 3 HTTP surface: ingest documents and semantic-search them.
 *
 * <pre>
 * # Ingest a document (chunk + embed + store in pgvector):
 * curl -s localhost:8080/api/embeddings/ingest \
 *   -H 'Content-Type: application/json' \
 *   -d '{"docName":"gst-notes","text":"... your document text ..."}'
 *
 * # Semantic search across all ingested documents:
 * curl -s localhost:8080/api/embeddings/search \
 *   -H 'Content-Type: application/json' \
 *   -d '{"query":"What is the GST rate for electronic goods?","k":3}'
 * </pre>
 */
@RestController
@RequestMapping("/api/embeddings")
public class SemanticSearchController {

    private final EmbeddingService embeddingService;

    public SemanticSearchController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostMapping("/ingest")
    public EmbeddingService.IngestResult ingest(@RequestBody IngestRequest request) {
        return embeddingService.ingest(request.docName(), request.text());
    }

    @PostMapping("/search")
    public List<ChunkRepository.SearchHit> search(@RequestBody SearchRequest request) {
        int k = request.k() == null ? 5 : request.k();
        return embeddingService.search(request.query(), k);
    }

    public record IngestRequest(@NotBlank String docName, @NotBlank String text) {
    }

    public record SearchRequest(
            @NotBlank String query,
            @Min(1) @Max(50) Integer k
    ) {
    }
}
