package com.dileep.ailearning.compliance;

import com.dileep.ailearning.compliance.dto.AuditComplianceQueryRequest;
import com.dileep.ailearning.compliance.dto.HybridSearchResult;
import com.dileep.ailearning.compliance.dto.IngestCircularRequest;
import com.dileep.ailearning.compliance.model.ComplianceAnswer;
import com.dileep.ailearning.compliance.service.ComplianceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Portfolio Blueprint 1: Compliance & Regulatory Document Engine.
 *
 * <p>Provides:
 * <ul>
 *   <li><b>Regulatory Ingestion:</b> Structured circular and clause ingestion with inline PII scrubbing.</li>
 *   <li><b>Hybrid Retrieval:</b> Dense vector cosine similarity fused with sparse BM25 text rank via RRF.</li>
 *   <li><b>Audited Regulatory Q&A:</b> Strict clause attribution and legal grounding for compliance officers.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/compliance")
public class ComplianceController {

    private final ComplianceService service;
    private final ObjectMapper objectMapper;

    public ComplianceController(ComplianceService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    /**
     * Ingest a regulatory circular with clauses.
     */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@Valid @RequestBody IngestCircularRequest request) {
        int ingested = service.ingest(request);
        return Map.of(
                "status", "success",
                "circularId", request.circularId(),
                "clausesIngested", ingested
        );
    }

    /**
     * Hybrid search across ingested regulatory circulars using Reciprocal Rank Fusion.
     */
    @PostMapping("/search")
    public List<HybridSearchResult> search(@RequestBody AuditComplianceQueryRequest request) {
        return service.searchHybrid(
                request.query(),
                request.circularIdFilter(),
                request.resolvedTopK()
        );
    }

    /**
     * Audited regulatory inquiry with clause-level citations and grounding checks.
     */
    @PostMapping("/audit-query")
    public ComplianceAnswer auditQuery(@Valid @RequestBody AuditComplianceQueryRequest request) {
        return service.auditQuery(request);
    }

    /**
     * Load bundled banking compliance circulars (RBI KYC Direction & Basel III LCR).
     */
    @PostMapping("/load-samples")
    public Map<String, Object> loadSamples() throws IOException {
        int rbiCount = loadSampleFile("samples/compliance-rbi-kyc.json");
        int baselCount = loadSampleFile("samples/compliance-basel-lcr.json");

        return Map.of(
                "status", "samples loaded",
                "rbiClauses", rbiCount,
                "baselClauses", baselCount,
                "totalClauses", service.getClauseCount()
        );
    }

    /**
     * Get total count of indexed compliance clauses.
     */
    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("totalClauses", service.getClauseCount());
    }

    /**
     * Delete a circular and its clauses.
     */
    @DeleteMapping("/circular/{circularId}")
    public Map<String, Object> deleteCircular(@PathVariable String circularId) {
        int deleted = service.deleteCircular(circularId);
        return Map.of("circularId", circularId, "deletedClauses", deleted);
    }

    private int loadSampleFile(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            IngestCircularRequest req = objectMapper.readValue(in, IngestCircularRequest.class);
            return service.ingest(req);
        }
    }
}
