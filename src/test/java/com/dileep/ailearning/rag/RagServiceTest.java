package com.dileep.ailearning.rag;

import com.dileep.ailearning.config.RagProperties;
import com.dileep.ailearning.embedding.ChunkRepository.Candidate;
import com.dileep.ailearning.embedding.EmbeddingService;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.ollama.dto.Message;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Module 4: the RAG orchestration — retrieve, re-rank, budget, ground, generate.
 * EmbeddingService and Ollama are mocked, so this runs without pgvector or a model.
 */
class RagServiceTest {

    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final OllamaClient ollama = mock(OllamaClient.class);

    private RagService newService(RagProperties config) {
        return new RagService(embeddingService, ollama, new RagPromptFactory(), config);
    }

    private static RagProperties config(int topK, int fetchK, boolean rerank, int maxContextChars) {
        return new RagProperties("test-chat", 0.1, 42, 8192, topK, fetchK, rerank, 0.6, maxContextChars);
    }

    private static Candidate cand(long id, String doc, int idx, String content, double sim, float[] emb) {
        return new Candidate(id, doc, idx, content, sim, emb);
    }

    private static ChatResponse chatResponse(String content) {
        return new ChatResponse("test-chat", new Message("assistant", content), true, 1_000L, 120, 30);
    }

    @Test
    void groundsAnswerAndReturnsCitations() {
        RagService service = newService(config(2, 20, true, 6000));
        when(embeddingService.retrieve(eq("what is GST?"), eq(20))).thenReturn(List.of(
                cand(1, "gst-notes", 0, "GST is a single indirect tax.", 0.92, new float[]{1, 0, 0}),
                cand(2, "gst-notes", 1, "The 18% slab covers electronics.", 0.81, new float[]{0, 1, 0}),
                cand(3, "other", 5, "Unrelated filler text here.", 0.40, new float[]{0, 0, 1})));
        when(ollama.chat(any())).thenReturn(chatResponse("GST is a single indirect tax [1]. Electronics are 18% [2]."));

        RagAnswer result = service.answer("what is GST?", null);

        assertThat(result.answer()).contains("[1]").contains("[2]");
        assertThat(result.model()).isEqualTo("test-chat");
        assertThat(result.retrieved()).isEqualTo(3);
        assertThat(result.used()).isEqualTo(2);          // topK = 2
        assertThat(result.reranked()).isTrue();
        assertThat(result.sources()).hasSize(2);
        assertThat(result.sources().get(0).ref()).isEqualTo(1);
        assertThat(result.sources().get(1).ref()).isEqualTo(2);
        assertThat(result.promptTokens()).isEqualTo(120);

        // The grounded prompt must contain the chunk text and the question.
        var captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(ollama).chat(captor.capture());
        String userPrompt = captor.getValue().messages().get(1).content();
        assertThat(userPrompt).contains("GST is a single indirect tax").contains("what is GST?");
        assertThat(captor.getValue().messages().get(0).role()).isEqualTo("system");
    }

    @Test
    void returnsNoAnswerWithoutCallingModelWhenNothingRetrieved() {
        RagService service = newService(config(4, 20, true, 6000));
        when(embeddingService.retrieve(any(), any(int.class))).thenReturn(List.of());

        RagAnswer result = service.answer("anything", null);

        assertThat(result.answer()).isEqualTo("I don't know based on the provided documents.");
        assertThat(result.sources()).isEmpty();
        assertThat(result.retrieved()).isZero();
        verify(ollama, never().description("must not waste a model call when there's nothing to ground on"))
                .chat(any());
    }

    @Test
    void contextBudgetCapsHowManyChunksAreStuffed() {
        // rerank off → deterministic similarity order; tiny budget → only 1 chunk fits.
        RagService service = newService(config(3, 20, false, 10));
        when(embeddingService.retrieve(any(), any(int.class))).thenReturn(List.of(
                cand(1, "doc", 0, "first chunk text that exceeds budget", 0.9, new float[]{1, 0}),
                cand(2, "doc", 1, "second chunk text", 0.8, new float[]{0, 1}),
                cand(3, "doc", 2, "third chunk text", 0.7, new float[]{1, 1})));
        when(ollama.chat(any())).thenReturn(chatResponse("Answer [1]."));

        RagAnswer result = service.answer("q", null);

        // Budget is 10 chars but we always keep at least the top chunk.
        assertThat(result.used()).isEqualTo(1);
        assertThat(result.sources()).hasSize(1);
    }

    @Test
    void topKOverrideWins() {
        RagService service = newService(config(4, 20, false, 6000));
        when(embeddingService.retrieve(any(), any(int.class))).thenReturn(List.of(
                cand(1, "doc", 0, "a", 0.9, new float[]{1, 0}),
                cand(2, "doc", 1, "b", 0.8, new float[]{0, 1}),
                cand(3, "doc", 2, "c", 0.7, new float[]{1, 1})));
        when(ollama.chat(any())).thenReturn(chatResponse("ok"));

        RagAnswer result = service.answer("q", 1);

        assertThat(result.used()).isEqualTo(1); // override of 1 beats configured topK of 4
    }

    @Test
    void rejectsBlankQuestion() {
        RagService service = newService(config(4, 20, true, 6000));
        assertThatThrownBy(() -> service.answer("  ", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
