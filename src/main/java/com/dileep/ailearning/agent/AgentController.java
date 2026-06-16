package com.dileep.ailearning.agent;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Module 5 HTTP surface: ask the tool-using agent a question.
 *
 * <pre>
 * # needs a tool-capable model, e.g. qwen2.5-coder:7b
 * curl -s localhost:8080/api/agent/ask \
 *   -H 'Content-Type: application/json' \
 *   -d '{"question":"What is the outstanding balance for customer C-100, and how close is it to their credit limit?"}'
 *
 * # see which tools are available
 * curl -s localhost:8080/api/agent/tools
 * </pre>
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final ToolRegistry toolRegistry;

    public AgentController(AgentService agentService, ToolRegistry toolRegistry) {
        this.agentService = agentService;
        this.toolRegistry = toolRegistry;
    }

    @PostMapping("/ask")
    public AgentResult ask(@RequestBody AskRequest request) {
        return agentService.run(request.question());
    }

    @GetMapping("/tools")
    public List<String> tools() {
        return toolRegistry.names();
    }

    public record AskRequest(@NotBlank String question) {
    }
}
