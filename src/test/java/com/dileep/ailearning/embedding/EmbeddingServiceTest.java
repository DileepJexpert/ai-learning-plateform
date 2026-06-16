package com.dileep.ailearning.embedding;

import com.dileep.ailearning.config.EmbeddingProperties;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.EmbedRequest;
import com.dileep.ailearning.ollama.dto.EmbedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Module 3 unit test: verifies that the service chunks, calls embed for each
 * chunk, and delegates storage to the repo. Ollama and the repo are mocked so
 * this runs without Docker/Postgres.
 */
class EmbeddingServiceTest {

    private OllamaClient ollama;
    private ChunkRepository chunkRepo;
    private EmbeddingService service;

    @BeforeEach
    void setUp() {
        ollama = mock(OllamaClient.class);
        chunkRepo = mock(ChunkRepository.class);
        var config = new EmbeddingProperties("nomic-embed-text", 4, 10, 3);
        service = new EmbeddingService(ollama, chunkRepo, config);
    }

    @Test
    void ingestChunksEmbedsThenStores() {
        // 20 chars, chunkSize=10, overlap=3 → step=7 → chunks at 0,7,14 → 3 chunks
        String text = "abcdefghij" + "klmnopqrst";

        when(ollama.embed(any())).thenReturn(embedResponse(new float[]{1, 0, 0, 0}));

        EmbeddingService.IngestResult result = service.ingest("test-doc", text);

        assertThat(result.docName()).isEqualTo("test-doc");
        assertThat(result.chunks()).isEqualTo(3);

        // One embed call per chunk
        verify(ollama, times(3)).embed(any());

        // Captures the embed requests to verify model
        var captor = ArgumentCaptor.forClass(EmbedRequest.class);
        verify(ollama, times(3)).embed(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(req ->
                assertThat(req.model()).isEqualTo("nomic-embed-text"));

        // Chunks stored
        verify(chunkRepo).deleteByDocName("test-doc");
        verify(chunkRepo).saveAll(any());
    }

    @Test
    void searchEmbedsQueryThenDelegatesToRepo() {
        when(ollama.embed(any())).thenReturn(embedResponse(new float[]{1, 0, 0, 0}));
        when(chunkRepo.searchSimilar(any(), any(int.class)))
                .thenReturn(List.of(new ChunkRepository.SearchHit(1, "doc", 0, "match", 0.95)));

        List<ChunkRepository.SearchHit> hits = service.search("what is GST?", 3);

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().similarity()).isEqualTo(0.95);

        // The query was embedded first
        verify(ollama).embed(any());
        verify(chunkRepo).searchSimilar(any(), any(int.class));
    }

    @Test
    void rejectsBlankInputs() {
        assertThatThrownBy(() -> service.ingest("", "text"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ingest("doc", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search("", 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static EmbedResponse embedResponse(float[] vector) {
        return new EmbedResponse("nomic-embed-text", List.of(vector), 1_000_000L);
    }
}
