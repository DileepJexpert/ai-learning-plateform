package com.dileep.ailearning.embedding;

import com.dileep.ailearning.config.EmbeddingProperties;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.EmbedRequest;
import com.dileep.ailearning.ollama.dto.EmbedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Module 3 — the full embed pipeline: {@code chunk → embed → store → search}.
 *
 * <p>This is the service layer that orchestrates:
 * <ol>
 *   <li>Split a document into overlapping chunks ({@link TextChunker})</li>
 *   <li>Embed each chunk via Ollama's embedding model (text → float[])</li>
 *   <li>Store chunks + vectors in pgvector ({@link ChunkRepository})</li>
 *   <li>At query time: embed the query, then cosine-similarity search</li>
 * </ol>
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final OllamaClient ollama;
    private final ChunkRepository chunkRepo;
    private final EmbeddingProperties config;

    public EmbeddingService(OllamaClient ollama, ChunkRepository chunkRepo,
                            EmbeddingProperties config) {
        this.ollama = ollama;
        this.chunkRepo = chunkRepo;
        this.config = config;
    }

    /**
     * Ingest a document: split into chunks, embed each, store in pgvector.
     *
     * @param docName  a label for this document (e.g. "gst-rules.txt")
     * @param text     the full document text
     * @return how many chunks were stored
     */
    public IngestResult ingest(String docName, String text) {
        if (docName == null || docName.isBlank()) {
            throw new IllegalArgumentException("docName must not be blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("document text must not be blank");
        }

        // 1. Chunk
        List<String> chunks = TextChunker.chunk(text, config.chunkSize(), config.chunkOverlap());
        log.info("ingest '{}': {} chars → {} chunks (size={}, overlap={})",
                docName, text.length(), chunks.size(), config.chunkSize(), config.chunkOverlap());

        // 2. Embed each chunk
        List<DocumentChunk> toStore = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            float[] vector = embed(chunks.get(i));
            toStore.add(DocumentChunk.of(docName, i, chunks.get(i), vector));
        }

        // 3. Delete any previous version of this document, then store
        int deleted = chunkRepo.deleteByDocName(docName);
        if (deleted > 0) {
            log.info("replaced {} existing chunks for '{}'", deleted, docName);
        }
        chunkRepo.saveAll(toStore);

        log.info("ingest '{}' complete: stored {} chunks (dims={})",
                docName, toStore.size(), toStore.getFirst().embedding().length);
        return new IngestResult(docName, chunks.size(), config.chunkSize(), config.chunkOverlap());
    }

    /**
     * Semantic search: embed the query, then find the most-similar stored chunks.
     *
     * @param query natural-language query
     * @param k     how many results to return
     */
    public List<ChunkRepository.SearchHit> search(String query, int k) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        float[] queryVector = embed(query);
        return chunkRepo.searchSimilar(queryVector, k);
    }

    /**
     * Retrieve the top-{@code k} candidate chunks for a query, <b>with</b> their
     * embeddings — used by RAG (Module 4) to fetch a candidate pool for re-ranking.
     */
    public List<ChunkRepository.Candidate> retrieve(String query, int k) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        return chunkRepo.searchCandidates(embed(query), k);
    }

    /** Embed a single text string using the configured embedding model. */
    private float[] embed(String text) {
        EmbedResponse response = ollama.embed(new EmbedRequest(config.model(), text));
        float[] vec = response.embedding();
        if (vec.length != config.dimensions()) {
            log.warn("expected {} dimensions but got {} — check embedding.dimensions config",
                    config.dimensions(), vec.length);
        }
        return vec;
    }

    public record IngestResult(String docName, int chunks, int chunkSize, int chunkOverlap) {
    }
}
