package com.dileep.ailearning.eval;

import com.dileep.ailearning.config.EvalProperties;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.ollama.dto.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Module 6: LLM-as-judge — we test verdict parsing and the pass threshold (Ollama mocked). */
class LlmJudgeTest {

    private final OllamaClient ollama = mock(OllamaClient.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final EvalProperties config = new EvalProperties("judge-model", 4, 0.8);
    private final LlmJudge judge = new LlmJudge(ollama, objectMapper, config);

    private static ChatResponse response(String content) {
        return new ChatResponse("judge-model", new Message("assistant", content), true, 1L, 10, 5);
    }

    @Test
    void highScorePasses() {
        when(ollama.chat(any())).thenReturn(response("{\"score\":5,\"reason\":\"fully grounded\"}"));

        JudgeVerdict verdict = judge.judgeFaithfulness("q", "a", "ctx");

        assertThat(verdict.score()).isEqualTo(5);
        assertThat(verdict.pass()).isTrue();
        assertThat(verdict.reason()).isEqualTo("fully grounded");

        // The judge must ask for JSON output.
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(ollama).chat(captor.capture());
        assertThat(captor.getValue().format()).isEqualTo("json");
        assertThat(captor.getValue().model()).isEqualTo("judge-model");
    }

    @Test
    void lowScoreFails() {
        when(ollama.chat(any())).thenReturn(response("{\"score\":2,\"reason\":\"unsupported\"}"));
        assertThat(judge.judgeFaithfulness("q", "a", "ctx").pass()).isFalse();
    }

    @Test
    void scoreIsClampedToRange() {
        assertThat(judge.parseVerdict("{\"score\":9}").score()).isEqualTo(5);
        assertThat(judge.parseVerdict("{\"score\":-3}").score()).isEqualTo(1);
    }

    @Test
    void unparseableVerdictFailsClosed() {
        JudgeVerdict verdict = judge.parseVerdict("the answer looks good to me");
        assertThat(verdict.score()).isZero();
        assertThat(verdict.pass()).isFalse();
    }
}
