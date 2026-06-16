package com.dileep.ailearning.agent;

import com.dileep.ailearning.agent.tools.ErpDataStore;
import com.dileep.ailearning.agent.tools.GetCustomerTool;
import com.dileep.ailearning.agent.tools.QueryLedgerTool;
import com.dileep.ailearning.config.AgentProperties;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.ollama.dto.FunctionCall;
import com.dileep.ailearning.ollama.dto.Message;
import com.dileep.ailearning.ollama.dto.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Module 5: the agentic loop. The model is mocked to script tool-call vs. final-answer
 * turns; the tools are real (backed by in-memory ERP data), so we exercise the whole
 * reason → act → observe → repeat cycle without a running model.
 */
class AgentServiceTest {

    private OllamaClient ollama;
    private AgentService service;

    @BeforeEach
    void setUp() {
        ollama = mock(OllamaClient.class);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ErpDataStore erp = new ErpDataStore();
        ToolRegistry registry = new ToolRegistry(List.of(
                new GetCustomerTool(erp, objectMapper),
                new QueryLedgerTool(erp, objectMapper)));
        var config = new AgentProperties("test-model", 0.0, 42, 8192, 5);
        service = new AgentService(ollama, registry, new AgentPromptFactory(), config);
    }

    @Test
    void callsToolThenReturnsFinalAnswer() {
        when(ollama.chat(any()))
                .thenReturn(toolCall("get_customer", Map.of("id", "C-100")))
                .thenReturn(finalAnswer("Bharat Motors Ltd has 142500.00 outstanding."));

        AgentResult result = service.run("What does customer C-100 owe?");

        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.stopped()).isFalse();
        assertThat(result.answer()).contains("Bharat Motors Ltd");
        assertThat(result.steps()).hasSize(1);

        AgentResult.ToolStep step = result.steps().get(0);
        assertThat(step.tool()).isEqualTo("get_customer");
        assertThat(step.ok()).isTrue();
        assertThat(step.result()).contains("Bharat Motors Ltd").contains("142500.00");

        verify(ollama, times(2)).chat(any());

        // The tool's result must have been fed back to the model as a tool-role message.
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(ollama, times(2)).chat(captor.capture());
        List<Message> finalConversation = captor.getValue().messages();
        assertThat(finalConversation)
                .anyMatch(m -> "tool".equals(m.role()) && m.content().contains("Bharat Motors Ltd"));
    }

    @Test
    void chainsMultipleToolCalls() {
        when(ollama.chat(any()))
                .thenReturn(toolCall("get_customer", Map.of("id", "C-100")))
                .thenReturn(toolCall("query_ledger", Map.of("account", "4000-SALES")))
                .thenReturn(finalAnswer("Done."));

        AgentResult result = service.run("Look up C-100 then the sales ledger");

        assertThat(result.iterations()).isEqualTo(3);
        assertThat(result.steps()).hasSize(2);
        assertThat(result.steps()).extracting(AgentResult.ToolStep::tool)
                .containsExactly("get_customer", "query_ledger");
        assertThat(result.steps()).allMatch(AgentResult.ToolStep::ok);
    }

    @Test
    void unknownToolIsFedBackAsErrorThenRecovers() {
        when(ollama.chat(any()))
                .thenReturn(toolCall("frobnicate", Map.of("x", "1")))
                .thenReturn(finalAnswer("Recovered."));

        AgentResult result = service.run("do something odd");

        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).ok()).isFalse();
        assertThat(result.steps().get(0).result())
                .contains("unknown tool 'frobnicate'")
                .contains("get_customer"); // lists what IS available
        assertThat(result.stopped()).isFalse();
        assertThat(result.answer()).isEqualTo("Recovered.");
    }

    @Test
    void missingRequiredArgumentIsRejected() {
        when(ollama.chat(any()))
                .thenReturn(toolCall("get_customer", Map.of())) // no "id"
                .thenReturn(finalAnswer("ok"));

        AgentResult result = service.run("look up a customer");

        AgentResult.ToolStep step = result.steps().get(0);
        assertThat(step.ok()).isFalse();
        assertThat(step.result()).contains("missing required argument").contains("id");
    }

    @Test
    void toolExecutionErrorIsFedBack() {
        when(ollama.chat(any()))
                .thenReturn(toolCall("get_customer", Map.of("id", "C-999"))) // not in the store
                .thenReturn(finalAnswer("No such customer exists."));

        AgentResult result = service.run("look up C-999");

        AgentResult.ToolStep step = result.steps().get(0);
        assertThat(step.ok()).isFalse();
        assertThat(step.result()).contains("No customer with id 'C-999'");
    }

    @Test
    void stopsAtMaxIterations() {
        // Model never stops asking for a tool → the loop must terminate at the cap.
        when(ollama.chat(any())).thenReturn(toolCall("get_customer", Map.of("id", "C-100")));

        AgentResult result = service.run("loop forever");

        assertThat(result.stopped()).isTrue();
        assertThat(result.iterations()).isEqualTo(5); // maxIterations
        assertThat(result.steps()).hasSize(5);
        verify(ollama, times(5)).chat(any());
    }

    @Test
    void advertisesToolsAndDecodingOptions() {
        when(ollama.chat(any())).thenReturn(finalAnswer("hi"));

        service.run("hello");

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(ollama).chat(captor.capture());
        ChatRequest sent = captor.getValue();
        assertThat(sent.model()).isEqualTo("test-model");
        assertThat(sent.tools()).hasSize(2);
        assertThat(sent.tools()).extracting(t -> t.function().name())
                .containsExactlyInAnyOrder("get_customer", "query_ledger");
        assertThat(sent.options().temperature()).isZero();
    }

    @Test
    void rejectsBlankQuestion() {
        assertThatThrownBy(() -> service.run("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    private static ChatResponse toolCall(String name, Map<String, Object> args) {
        Message msg = new Message("assistant", "", null,
                List.of(new ToolCall(new FunctionCall(name, args))), null);
        return new ChatResponse("test-model", msg, true, 1_000L, 50, 10);
    }

    private static ChatResponse finalAnswer(String text) {
        return new ChatResponse("test-model", new Message("assistant", text), true, 1_000L, 50, 10);
    }
}
