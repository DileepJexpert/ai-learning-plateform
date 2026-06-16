package com.dileep.ailearning.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module 4: the grounding rules live in the prompt, so we guard them.
 */
class RagPromptFactoryTest {

    private final RagPromptFactory factory = new RagPromptFactory();

    @Test
    void systemPromptEnforcesGrounding() {
        String prompt = factory.systemPrompt();
        assertThat(prompt)
                .contains("ONLY")                               // answer only from context
                .contains("Cite")                               // citations required
                .contains("I don't know based on the provided documents.") // refusal phrase
                .contains("Do not guess");                      // no fabrication
    }

    @Test
    void userPromptCarriesContextAndQuestion() {
        String prompt = factory.userPrompt("[1] (doc) some fact", "What is the rate?");
        assertThat(prompt)
                .contains("[1] (doc) some fact")
                .contains("What is the rate?")
                .contains("cite sources as [n]");
    }
}
