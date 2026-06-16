package com.dileep.ailearning.agent;

import com.dileep.ailearning.ollama.dto.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds every {@link Tool} the agent can use (Module 5).
 *
 * <p>Spring injects all {@code Tool} beans, so the registry is automatically
 * complete — define a new {@code @Component implements Tool} and it's available to
 * the agent with no wiring changes.
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> byName = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> tools) {
        for (Tool tool : tools) {
            byName.put(tool.name(), tool);
        }
    }

    /** Look up a tool by the name the model used (empty if it hallucinated a name). */
    public Optional<Tool> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** All tool names (used in error messages when the model picks an unknown tool). */
    public List<String> names() {
        return List.copyOf(byName.keySet());
    }

    /** The wire-format tool definitions to advertise to Ollama. */
    public List<ToolDefinition> definitions() {
        return byName.values().stream().map(Tool::definition).toList();
    }
}
