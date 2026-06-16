package com.dileep.ailearning.agent;

import com.dileep.ailearning.ollama.dto.FunctionDefinition;
import com.dileep.ailearning.ollama.dto.ToolDefinition;

import java.util.List;
import java.util.Map;

/**
 * A capability the agent can invoke (Module 5).
 *
 * <p>Each tool exposes:
 * <ul>
 *   <li>a {@link #name()} the model uses to call it,</li>
 *   <li>a {@link #description()} the model reads to decide <i>when</i> to call it,</li>
 *   <li>a {@link #parameters()} JSON Schema describing its arguments, and</li>
 *   <li>{@link #execute(Map)} which runs the real Spring Boot logic.</li>
 * </ul>
 *
 * <p>Implementations are just Spring beans — {@code ToolRegistry} auto-discovers
 * all of them, so adding a new capability is a one-class change.
 */
public interface Tool {

    String name();

    String description();

    /** JSON Schema (type "object") describing the arguments. */
    Map<String, Object> parameters();

    /** Parameter names that must be present. Used for cheap validation before execution. */
    List<String> requiredParameters();

    /**
     * Run the tool. Return a string (usually JSON) that is fed back to the model.
     *
     * @throws ToolExecutionException if the arguments are valid-shaped but the
     *                                operation fails (e.g. id not found) — the
     *                                message is fed back so the model can adapt.
     */
    String execute(Map<String, Object> arguments);

    /** Build the wire-format definition advertised to Ollama. */
    default ToolDefinition definition() {
        return ToolDefinition.of(new FunctionDefinition(name(), description(), parameters()));
    }
}
