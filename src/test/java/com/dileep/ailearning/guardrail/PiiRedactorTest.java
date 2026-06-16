package com.dileep.ailearning.guardrail;

import com.dileep.ailearning.guardrail.PiiRedactor.PiiType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Module 6: PII detection and masking — pure logic, the redact-before-log control. */
class PiiRedactorTest {

    private final PiiRedactor redactor = new PiiRedactor();

    @Test
    void redactsEmail() {
        var result = redactor.redact("Contact me at john.doe@acme.co.in please");
        assertThat(result.redactedText()).contains("[REDACTED_EMAIL]").doesNotContain("john.doe@acme.co.in");
        assertThat(result.counts()).containsEntry(PiiType.EMAIL, 1);
    }

    @Test
    void redactsIndianIdentifiers() {
        assertThat(redactor.detectedTypes("PAN is ABCPK1234Z")).containsExactly(PiiType.PAN);
        assertThat(redactor.detectedTypes("Aadhaar 2345 6789 0123")).containsExactly(PiiType.AADHAAR);
        assertThat(redactor.detectedTypes("GSTIN 27AABCA1234A1Z5")).containsExactly(PiiType.GSTIN);
    }

    @Test
    void redactsCreditCardAndPhone() {
        var result = redactor.redact("card 4111 1111 1111 1111 phone 9876543210");
        assertThat(result.counts()).containsEntry(PiiType.CREDIT_CARD, 1).containsEntry(PiiType.PHONE, 1);
        assertThat(result.redactedText()).doesNotContain("4111").doesNotContain("9876543210");
    }

    @Test
    void gstinIsNotDoubleCountedAsPan() {
        // A GSTIN embeds a PAN; because GSTIN is matched first, we count it once as GSTIN.
        var result = redactor.redact("GSTIN 27AABCA1234A1Z5");
        assertThat(result.counts()).containsOnlyKeys(PiiType.GSTIN);
    }

    @Test
    void countsMultipleAcrossTypes() {
        String text = "Email a@b.com, PAN ABCPK1234Z, card 4111 1111 1111 1111, phone 9876543210";
        var result = redactor.redact(text);
        assertThat(result.counts())
                .containsEntry(PiiType.EMAIL, 1)
                .containsEntry(PiiType.PAN, 1)
                .containsEntry(PiiType.CREDIT_CARD, 1)
                .containsEntry(PiiType.PHONE, 1);
    }

    @Test
    void cleanTextHasNoPii() {
        var result = redactor.redact("The quarterly sales report is attached.");
        assertThat(result.counts()).isEmpty();
        assertThat(redactor.containsPii("nothing sensitive here")).isFalse();
    }
}
