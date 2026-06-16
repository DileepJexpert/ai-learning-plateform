package com.dileep.ailearning.agent;

import com.dileep.ailearning.agent.AgentResult.ToolStep;
import com.dileep.ailearning.config.AgentProperties;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.ollama.dto.FunctionCall;
import com.dileep.ailearning.ollama.dto.Message;
import com.dileep.ailearning.ollama.dto.Options;
import com.dileep.ailearning.ollama.dto.ToolCall;
import com.dileep.ailearning.ollama.dto.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Module 5 — the agentic loop (ReAct: reason → act → observe → repeat).
 *
 * <p>We advertise the available {@link Tool}s to the model. Each turn the model
 * either:
 * <ul>
 *   <li>asks to call one or more tools → we execute them and feed the results back, or</li>
 *   <li>returns a final answer → we're done.</li>
 * </ul>
 *
 * <p>Two guardrails make this safe rather than chaotic:
 * <ol>
 *   <li><b>Validation + error-feedback:</b> unknown tool, missing argument, or a
 *       failed execution is caught and returned to the model <i>as the tool result</i>,
 *       so it can self-correct instead of crashing the request. (Same philosophy as
 *       Module 1's auto-retry.)</li>
 *   <li><b>A hard iteration cap</b> so a confused model can't loop forever.</li>
 * </ol>
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final OllamaClient ollama;
    private final ToolRegistry tools;
    private final AgentPromptFactory prompts;
    private final AgentProperties config;

    public AgentService(OllamaClient ollama, ToolRegistry tools,
                        AgentPromptFactory prompts, AgentProperties config) {
        this.ollama = ollama;
        this.tools = tools;
        this.prompts = prompts;
        this.config = config;
    }

    public AgentResult run(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        long startNanos = System.nanoTime();

        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(prompts.systemPrompt()));
        messages.add(Message.user(question));

        Options options = new Options(config.temperature(), null, config.seed(), config.numCtx());
        List<ToolDefinition> toolDefs = tools.definitions();
        List<ToolStep> trace = new ArrayList<>();

        for (int iteration = 1; iteration <= config.maxIterations(); iteration++) {
            ChatResponse response = ollama.chat(
                    ChatRequest.withTools(config.model(), messages, options, toolDefs));
            Message reply = response.message();

            // No tool calls => the model produced its final answer.
            if (reply == null || !reply.hasToolCalls()) {
                String answer = reply == null ? "" : reply.content().strip();
                long durationMs = elapsedMs(startNanos);
                log.info("agent done: iterations={} toolCalls={} {}ms", iteration, trace.size(), durationMs);
                return new AgentResult(answer, List.copyOf(trace), iteration, false,
                        config.model(), durationMs);
            }

            // Record the assistant's tool-call turn, then execute each call and feed results back.
            messages.add(reply);
            for (ToolCall call : reply.toolCalls()) {
                ToolStep step = executeToolCall(call);
                trace.add(step);
                messages.add(Message.toolResult(step.tool(), step.result()));
            }
        }

        // Hit the iteration cap without a final answer.
        long durationMs = elapsedMs(startNanos);
        log.warn("agent stopped at max iterations ({})", config.maxIterations());
        return new AgentResult(
                "Reached the maximum number of tool iterations (" + config.maxIterations()
                        + ") without producing a final answer.",
                List.copyOf(trace), config.maxIterations(), true, config.model(), durationMs);
    }

    /**
     * Run one tool call with validation. Any problem (unknown tool, missing
     * argument, execution failure) becomes a non-ok step whose message is fed back
     * to the model rather than thrown.
     */
    private ToolStep executeToolCall(ToolCall call) {
        FunctionCall fn = call.function();
        String name = fn == null ? null : fn.name();
        Map<String, Object> args = (fn == null || fn.arguments() == null) ? Map.of() : fn.arguments();

        if (name == null || name.isBlank()) {
            return new ToolStep("(unknown)", args, "Error: malformed tool call with no function name.", false);
        }

        Tool tool = tools.find(name).orElse(null);
        if (tool == null) {
            log.warn("agent: model called unknown tool '{}'", name);
            return new ToolStep(name, args,
                    "Error: unknown tool '" + name + "'. Available tools: " + tools.names(), false);
        }

        List<String> missing = tool.requiredParameters().stream()
                .filter(p -> !args.containsKey(p) || isBlank(args.get(p)))
                .toList();
        if (!missing.isEmpty()) {
            return new ToolStep(name, args,
                    "Error: missing required argument(s) " + missing + " for tool '" + name + "'.", false);
        }

        try {
            String result = tool.execute(args);
            log.info("agent: called {} args={} -> ok", name, args);
            return new ToolStep(name, args, result, true);
        } catch (ToolExecutionException e) {
            log.info("agent: called {} args={} -> error: {}", name, args, e.getMessage());
            return new ToolStep(name, args, "Error: " + e.getMessage(), false);
        }
    }

    private static boolean isBlank(Object value) {
        return value == null || value.toString().isBlank();
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
