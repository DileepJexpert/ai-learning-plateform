package com.dileep.ailearning.invoice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The prompt IS the feature in Module 1, so we guard its key properties: it must
 * carry the schema, the few-shot examples, and the null-handling rule.
 */
class InvoicePromptFactoryTest {

    private final InvoicePromptFactory factory = new InvoicePromptFactory();

    @Test
    void systemPromptContainsSchemaAndRules() {
        String prompt = factory.systemPrompt();
        // every schema key the model must emit
        assertThat(prompt)
                .contains("invoiceNumber")
                .contains("lineItems")
                .contains("hsnCode")
                .contains("totalAmount");
        // the core reliability rules
        assertThat(prompt)
                .contains("use null")
                .contains("NEVER guess")
                .contains("JSON only");
    }

    @Test
    void systemPromptContainsTwoFewShotExamples() {
        String prompt = factory.systemPrompt();
        assertThat(prompt).contains("EXAMPLE 1").contains("EXAMPLE 2");
        // the second example demonstrates nulls for absent fields
        assertThat(prompt).contains("\"vendorGstin\":null");
    }

    @Test
    void userPromptCarriesTheRawInvoice() {
        String raw = "Invoice No: ABC-123";
        assertThat(factory.userPrompt(raw)).contains(raw);
    }

    @Test
    void correctionPromptIncludesTheProblem() {
        String problem = "lineItems: must not be empty";
        assertThat(factory.correctionPrompt(problem)).contains(problem);
    }
}
