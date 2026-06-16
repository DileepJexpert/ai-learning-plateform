package com.dileep.ailearning.guardrail;

import com.dileep.ailearning.invoice.model.Invoice;
import com.dileep.ailearning.invoice.model.LineItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Module 6: the business-rule guardrail that gates a DB write — pure logic. */
class InvoiceGuardrailTest {

    private final InvoiceGuardrail guardrail = new InvoiceGuardrail();

    private static BigDecimal bd(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    private static Invoice invoice(String currency, String vendorGstin,
                                   String subtotal, String tax, String total, List<LineItem> items) {
        return new Invoice("INV-1", "2025-01-01", "Acme", vendorGstin, null, null, currency,
                items, bd(subtotal), bd(tax), bd(total));
    }

    @Test
    void cleanInvoicePasses() {
        var items = List.of(new LineItem("Widget", "8471", bd("2"), bd("50.00"), bd("18"), bd("100.00")));
        Invoice invoice = invoice("INR", "27AABCA1234A1Z5", "100.00", "18.00", "118.00", items);

        assertThat(guardrail.check(invoice)).isEmpty();
        assertThat(guardrail.isClean(invoice)).isTrue();
    }

    @Test
    void rejectsDisallowedCurrency() {
        Invoice invoice = invoice("XYZ", null, null, null, null, List.of(new LineItem("W", null, null, null, null, null)));
        assertThat(guardrail.check(invoice)).anyMatch(v -> v.contains("currency 'XYZ'"));
    }

    @Test
    void rejectsMalformedGstin() {
        Invoice invoice = invoice("INR", "NOT-A-GSTIN", null, null, null,
                List.of(new LineItem("W", null, null, null, null, null)));
        assertThat(guardrail.check(invoice)).anyMatch(v -> v.contains("vendorGstin"));
    }

    @Test
    void rejectsTotalsThatDoNotAddUp() {
        // 100 + 18 = 118, but total says 200
        Invoice invoice = invoice("INR", null, "100.00", "18.00", "200.00",
                List.of(new LineItem("W", null, null, null, null, null)));
        assertThat(guardrail.check(invoice)).anyMatch(v -> v.contains("does not equal totalAmount"));
    }

    @Test
    void rejectsLineThatDoesNotMultiplyOut() {
        // 2 × 50 = 100, but lineTotal says 999
        var items = List.of(new LineItem("Widget", null, bd("2"), bd("50.00"), null, bd("999.00")));
        Invoice invoice = invoice("INR", null, null, null, null, items);
        assertThat(guardrail.check(invoice)).anyMatch(v -> v.contains("quantity × unitPrice"));
    }

    @Test
    void toleratesNullsForOptionalFields() {
        // No currency, no GSTIN, no totals — nothing to validate, so no violations.
        var items = List.of(new LineItem("Service", null, null, null, null, bd("45000.00")));
        Invoice invoice = invoice(null, null, null, null, null, items);
        assertThat(guardrail.check(invoice)).isEmpty();
    }
}
