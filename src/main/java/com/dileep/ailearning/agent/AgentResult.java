package com.dileep.ailearning.agent;

import java.util.List;
import java.util.Map;

/**
 * The outcome of an agent run (Module 5): the final answer plus a full trace of
 * the tool calls it made along the way.
 *
 * <p>The {@code steps} trace is what makes the agentic loop observable and
 * debuggable — you can see exactly what the model decided to call, with what
 * arguments, and what came back. (Invaluable for the "how do agents work?"
 * interview question, and for production debugging.)
 *
 * @param answer     the model's final natural-language answer
 * @param steps      ordered tool calls that were executed
 * @param iterations how many model round-trips the loop took
 * @param stopped    true if the loop hit the max-iteration cap before finishing
 * @param model      model used
 * @param durationMs wall-clock time for the whole run
 */
public record AgentResult(
        String answer,
        List<ToolStep> steps,
        int iterations,
        boolean stopped,
        String model,
        long durationMs
) {

    /**
     * One executed tool call.
     *
     * @param tool      the tool name the model invoked
     * @param arguments the arguments it supplied
     * @param result    the string returned to the model (a result, or an error message)
     * @param ok        false if the call was rejected (unknown tool / bad args / execution error)
     */
    public record ToolStep(String tool, Map<String, Object> arguments, String result, boolean ok) {
    }
}
