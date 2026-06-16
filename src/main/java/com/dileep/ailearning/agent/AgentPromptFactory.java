package com.dileep.ailearning.agent;

import org.springframework.stereotype.Component;

/**
 * Builds the agent's system prompt (Module 5).
 *
 * <p>The prompt establishes the ReAct-style contract: use tools to fetch real
 * data, never invent it, and stop once you can answer. The tool <i>schemas</i> are
 * sent separately in the request's {@code tools} field — the model decides which to
 * call — so the prompt only needs to set the behaviour and guardrails.
 */
@Component
public class AgentPromptFactory {

    public String systemPrompt() {
        return """
                You are an ERP assistant for an accounts team. You can call tools to look up
                real customer and ledger data.

                HOW TO WORK
                - When a question needs customer or ledger data, CALL the appropriate tool.
                  Never guess or invent ids, balances, names, or figures.
                - Use the exact argument names and types described by each tool's schema.
                - If a tool returns an error (e.g. an unknown id), read it and adjust: try a
                  valid id from the error, or tell the user what's available.
                - You may call tools several times if needed (e.g. look up a customer, then its ledger).
                - When you have enough information, STOP calling tools and give a concise, factual
                  final answer that directly addresses the question. Quote figures exactly.
                """;
    }
}
