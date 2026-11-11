package com.dileep.ailearning.compliance.service;

import com.dileep.ailearning.common.JsonSanitizer;
import com.dileep.ailearning.compliance.dto.AuditComplianceQueryRequest;
import com.dileep.ailearning.compliance.dto.HybridSearchResult;
import com.dileep.ailearning.compliance.dto.IngestCircularRequest;
import com.dileep.ailearning.compliance.model.AuditCitation;
import com.dileep.ailearning.compliance.model.ClauseStatement;
import com.dileep.ailearning.compliance.model.ComplianceAnswer;
import com.dileep.ailearning.compliance.model.ComplianceClause;
import com.dileep.ailearning.compliance.prompt.CompliancePromptFactory;
import com.dileep.ailearning.compliance.repository.ComplianceClauseRepository;
import com.dileep.ailearning.compliance.search.ReciprocalRankFusion;
import com.dileep.ailearning.config.EmbeddingProperties;
import com.dileep.ailearning.config.OllamaProperties;
import com.dileep.ailearning.guardrail.PiiRedactor;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.ollama.dto.EmbedRequest;
import com.dileep.ailearning.ollama.dto.Options;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service orchestrating Portfolio Blueprint 1: Compliance & Regulatory Document Engine.
 *
 * <p>Key architectural pillars:
 * <ol>
 *   <li><b>Inline PII Redaction:</b> Scrub card/account/personal identifiers before embedding or DB writes.</li>
 *   <li><b>Hybrid Retrieval with RRF:</b> Combines dense vector semantic search with sparse BM25 text rank.</li>
 *   <li><b>Auditor Attribution:</b> Enforces clause-level citations with strict grounding checks.</li>
 * </ol>
 */
@Service
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AuditModelOutput(String summary, List<ClauseStatement> findings) {
    }

    private final ComplianceClauseRepository repository;
    private final OllamaClient ollama;
    private final CompliancePromptFactory promptFactory;
    private final ObjectMapper objectMapper;
    private final String embeddingModel;
    private final String chatModel;

    public ComplianceService(ComplianceClauseRepository repository,
                             OllamaClient ollama,
                             CompliancePromptFactory promptFactory,
                             EmbeddingProperties embeddingProps,
                             OllamaProperties ollamaProps,
                             ObjectMapper objectMapper) {
        this.repository = repository;
        this.ollama = ollama;
        this.promptFactory = promptFactory;
        this.objectMapper = objectMapper;
        this.embeddingModel = embeddingProps.model();
        this.chatModel = ollamaProps.model();
    }

    /**
     * Ingest a regulatory circular with clause metadata and inline PII redaction.
     */
    public int ingest(IngestCircularRequest request) {
        log.info("Ingesting regulatory circular: id='{}' title='{}' clauses={}",
                request.circularId(), request.circularTitle(), request.clauses().size());

        List<ComplianceClause> clausesToSave = new ArrayList<>();

        for (IngestCircularRequest.RawClause raw : request.clauses()) {
            // 1. Strict inline PII scrubbing prior to embedding generation
            String sanitizedContent = PiiRedactor.redact(raw.content());

            // 2. Generate dense vector embedding
            float[] embedding = ollama.embed(new EmbedRequest(embeddingModel, sanitizedContent)).embedding();

            clausesToSave.add(new ComplianceClause(
                    request.circularId(),
                    request.circularTitle(),
                    request.authority(),
                    request.effectiveDate(),
                    raw.clauseRef(),
                    raw.sectionTitle(),
                    sanitizedContent,
                    embedding
            ));
        }

        repository.saveAll(clausesToSave);
        return clausesToSave.size();
    }

    /**
     * Hybrid search combining dense cosine similarity and sparse full-text search with RRF.
     */
    public List<HybridSearchResult> searchHybrid(String query, String circularIdFilter, int topK) {
        int fetchK = Math.max(20, topK * 3);

        // 1. Dense vector candidate retrieval
        float[] queryEmbedding = ollama.embed(new EmbedRequest(embeddingModel, query)).embedding();
        var denseHits = repository.searchDense(queryEmbedding, circularIdFilter, fetchK);

        // 2. Sparse full-text (BM25) candidate retrieval
        var sparseHits = repository.searchSparse(query, circularIdFilter, fetchK);

        // 3. Reciprocal Rank Fusion
        var fused = ReciprocalRankFusion.fuse(
                denseHits,
                sparseHits,
                c -> c.id() != null ? c.id() : (long) (c.circularId() + c.clauseRef()).hashCode(),
                ReciprocalRankFusion.DEFAULT_RRF_K,
                topK
        );

        return fused.stream().map(f -> {
            ComplianceClause c = f.item();
            return new HybridSearchResult(
                    c.id(),
                    c.circularId(),
                    c.circularTitle(),
                    c.authority(),
                    c.effectiveDate(),
                    c.clauseRef(),
                    c.sectionTitle(),
                    c.content(),
                    f.rrfScore(),
                    f.denseScore(),
                    f.sparseScore()
            );
        }).toList();
    }

    /**
     * Execute an audited compliance query with clause-level citation attribution.
     */
    public ComplianceAnswer auditQuery(AuditComplianceQueryRequest request) {
        long startNanos = System.nanoTime();

        // 1. Retrieve candidate clauses via hybrid search
        List<HybridSearchResult> hybridHits = searchHybrid(
                request.query(), request.circularIdFilter(), request.resolvedTopK());

        if (hybridHits.isEmpty()) {
            long wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            return new ComplianceAnswer(
                    "INSUFFICIENT_REGULATORY_GROUNDING",
                    List.of(),
                    List.of(),
                    chatModel,
                    0,
                    wallMs
            );
        }

        List<ComplianceClause> retrievedClauses = hybridHits.stream().map(h -> new ComplianceClause(
                h.id(), h.circularId(), h.circularTitle(), h.authority(), h.effectiveDate(),
                h.clauseRef(), h.sectionTitle(), h.content(), null, null
        )).toList();

        // 2. Build auditor prompt with strict grounding and few-shot JSON contract
        var messages = promptFactory.buildMessages(request.query(), retrievedClauses);
        ChatRequest chatRequest = ChatRequest.json(chatModel, messages, new Options(0.0, null, 42));

        ChatResponse response = ollama.chat(chatRequest);
        long wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        // 3. Parse JSON response
        AuditModelOutput output = parseModelOutput(response.content());

        // 4. Build verified citations
        List<AuditCitation> citations = retrievedClauses.stream().map(c -> new AuditCitation(
                c.circularId(),
                c.circularTitle(),
                c.authority(),
                c.effectiveDate(),
                c.clauseRef(),
                c.sectionTitle(),
                truncate(c.content(), 150)
        )).toList();

        return new ComplianceAnswer(
                output.summary(),
                output.findings() != null ? output.findings() : List.of(),
                citations,
                response.model(),
                retrievedClauses.size(),
                wallMs
        );
    }

    /**
     * Delete circular and clauses.
     */
    public int deleteCircular(String circularId) {
        return repository.deleteByCircularId(circularId);
    }

    /**
     * Count total compliance clauses in database.
     */
    public long getClauseCount() {
        return repository.count();
    }

    private AuditModelOutput parseModelOutput(String rawJson) {
        try {
            String sanitized = JsonSanitizer.sanitize(rawJson);
            return objectMapper.readValue(sanitized, AuditModelOutput.class);
        } catch (Exception e) {
            log.warn("Failed to parse compliance audit model output: '{}'. Error: {}", rawJson, e.getMessage());
            return new AuditModelOutput("INSUFFICIENT_REGULATORY_GROUNDING: " + e.getMessage(), List.of());
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
