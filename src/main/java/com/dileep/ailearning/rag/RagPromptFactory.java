package com.dileep.ailearning.rag;

import org.springframework.stereotype.Component;

/**
 * Builds the RAG prompts (Module 4).
 *
 * <p>The system prompt is where <b>grounding</b> happens — the single most
 * important lever for reducing hallucination. We tell the model, in no uncertain
 * terms, to answer <em>only</em> from the supplied context, to cite which numbered
 * source it used, and to admit when the answer isn't there rather than invent one.
 */
@Component
public class RagPromptFactory {

    /** The grounding contract. */
    public String systemPrompt() {
        return """
                You are a careful assistant that answers questions using ONLY the provided context.

                RULES
                1. Use only the information in the numbered sources below. Do NOT use any outside
                   or prior knowledge.
                2. Cite the sources you rely on using their number in square brackets, e.g. [1] or [2][3].
                3. If the context does not contain enough information to answer, reply exactly:
                   "I don't know based on the provided documents."
                   Do not guess or fabricate.
                4. Be concise and factual. Quote figures and codes exactly as written.
                """;
    }

    /**
     * The data: numbered context block + the user's question.
     *
     * @param numberedContext sources formatted as "[1] (doc) text\n\n[2] (doc) text..."
     * @param question        the user's question
     */
    public String userPrompt(String numberedContext, String question) {
        return """
                Context:
                %s

                Question: %s

                Answer (cite sources as [n]):""".formatted(numberedContext, question);
    }
}
