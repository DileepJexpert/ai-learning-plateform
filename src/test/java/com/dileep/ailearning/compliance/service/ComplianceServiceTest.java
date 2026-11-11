package com.dileep.ailearning.compliance.service;

import com.dileep.ailearning.compliance.dto.AuditComplianceQueryRequest;
import com.dileep.ailearning.compliance.dto.HybridSearchResult;
import com.dileep.ailearning.compliance.dto.IngestCircularRequest;
import com.dileep.ailearning.compliance.model.ComplianceAnswer;
import com.dileep.ailearning.compliance.model.ComplianceClause;
import com.dileep.ailearning.compliance.prompt.CompliancePromptFactory;
import com.dileep.ailearning.compliance.repository.ComplianceClauseRepository;
import com.dileep.ailearning.compliance.search.ReciprocalRankFusion.ScoredItem;
import com.dileep.ailearning.config.EmbeddingProperties;
import com.dileep.ailearning.config.OllamaProperties;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.ollama.dto.EmbedRequest;
import com.dileep.ailearning.ollama.dto.EmbedResponse;
import com.dileep.ailearning.ollama.dto.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ComplianceServiceTest {

    private final ComplianceClauseRepository repository = mock(ComplianceClauseRepository.class);
    private final OllamaClient ollama = mock(OllamaClient.class);
    private final CompliancePromptFactory promptFactory = new CompliancePromptFactory();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmbeddingProperties embeddingProps = new EmbeddingProperties("nomic-embed-text", 768, 500, 100);
    private final OllamaProperties ollamaProps = new OllamaProperties("http://test", "qwen2.5-coder:7b", Duration.ofSeconds(5), Duration.ofSeconds(30));

    private ComplianceService service;

    @BeforeEach
    void setUp() {
        service = new ComplianceService(repository, ollama, promptFactory, embeddingProps, ollamaProps, objectMapper);
    }

    @Test
    void inlinesPiiRedactionDuringIngestion() {
        when(ollama.embed(any(EmbedRequest.class))).thenReturn(new EmbedResponse(new float[]{0.1f, 0.2f}));

        IngestCircularRequest request = new IngestCircularRequest(
                "CIRC-01",
                "Account Review Guidelines",
                "Internal Risk",
                "2024-01-01",
                List.of(new IngestCircularRequest.RawClause(
                        "Sec 1",
                        "Audit",
                        "Audit for account with PAN ABCPK1234Z and card 4111 1111 1111 1111"
                ))
        );

        int ingested = service.ingest(request);

        assertThat(ingested).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ComplianceClause>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        ComplianceClause saved = captor.getValue().get(0);
        assertThat(saved.content()).contains("[PAN_REDACTED]");
        assertThat(saved.content()).contains("[CARD_REDACTED]");
        assertThat(saved.content()).doesNotContain("ABCPK1234Z");
    }

    @Test
    void searchHybridExecutesDenseAndSparseWithRrf() {
        float[] vector = new float[]{0.1f, 0.2f};
        when(ollama.embed(any(EmbedRequest.class))).thenReturn(new EmbedResponse(vector));

        ComplianceClause clause = new ComplianceClause(
                1L, "RBI/2024/01", "KYC", "RBI", "2024-01-01", "Sec 4.2", "Intervals",
                "High risk accounts must be updated every 2 years.", null, null);

        when(repository.searchDense(eq(vector), isNull(), anyInt()))
                .thenReturn(List.of(new ScoredItem<>(clause, 0.92)));
        when(repository.searchSparse(eq("KYC intervals"), isNull(), anyInt()))
                .thenReturn(List.of(new ScoredItem<>(clause, 3.5)));

        List<HybridSearchResult> results = service.searchHybrid("KYC intervals", null, 5);

        assertThat(results).hasSize(1);
        HybridSearchResult hit = results.get(0);
        assertThat(hit.circularId()).isEqualTo("RBI/2024/01");
        assertThat(hit.denseScore()).isEqualTo(0.92);
        assertThat(hit.sparseScore()).isEqualTo(3.5);
        assertThat(hit.rrfScore()).isGreaterThan(0.0);
    }

    @Test
    void auditQuerySynthesizesAuditedFindingsAndCitations() {
        float[] vector = new float[]{0.1f, 0.2f};
        when(ollama.embed(any(EmbedRequest.class))).thenReturn(new EmbedResponse(vector));

        ComplianceClause clause = new ComplianceClause(
                10L, "RBI/2024-25/112", "KYC Master Direction", "RBI", "2024-04-01",
                "Section 4.2(a)", "Periodic Updation",
                "Periodic updation shall be carried out every 2 years for high risk.", null, null);

        when(repository.searchDense(any(), isNull(), anyInt()))
                .thenReturn(List.of(new ScoredItem<>(clause, 0.95)));
        when(repository.searchSparse(any(), isNull(), anyInt()))
                .thenReturn(List.of(new ScoredItem<>(clause, 4.0)));

        String modelJson = """
                {
                  "summary": "High-risk accounts require KYC refresh every 2 years.",
                  "findings": [
                    {
                      "finding": "Mandatory 2-year refresh interval for high risk.",
                      "circularId": "RBI/2024-25/112",
                      "clauseRef": "Section 4.2(a)",
                      "confidence": 0.99
                    }
                  ]
                }
                """;
        when(ollama.chat(any())).thenReturn(
                new ChatResponse("qwen2.5-coder:7b", Message.assistant(modelJson), true, 1000L, 100, 50));

        ComplianceAnswer answer = service.auditQuery(new AuditComplianceQueryRequest("What is the high risk interval?", null, 3));

        assertThat(answer.summary()).contains("High-risk accounts require KYC refresh every 2 years.");
        assertThat(answer.auditedFindings()).hasSize(1);
        assertThat(answer.auditedFindings().get(0).circularId()).isEqualTo("RBI/2024-25/112");
        assertThat(answer.auditedFindings().get(0).clauseRef()).isEqualTo("Section 4.2(a)");
        assertThat(answer.verifiedCitations()).hasSize(1);
        assertThat(answer.verifiedCitations().get(0).authority()).isEqualTo("RBI");
    }

    @Test
    void auditQueryReturnsInsufficientGroundingWhenNoClausesFound() {
        when(ollama.embed(any(EmbedRequest.class))).thenReturn(new EmbedResponse(new float[]{0.1f}));
        when(repository.searchDense(any(), any(), anyInt())).thenReturn(List.of());
        when(repository.searchSparse(any(), any(), anyInt())).thenReturn(List.of());

        ComplianceAnswer answer = service.auditQuery(new AuditComplianceQueryRequest("Unknown rule", null, 3));

        assertThat(answer.summary()).isEqualTo("INSUFFICIENT_REGULATORY_GROUNDING");
        assertThat(answer.auditedFindings()).isEmpty();
        assertThat(answer.verifiedCitations()).isEmpty();
    }
}
