package com.dileep.ailearning.eval;

import com.dileep.ailearning.invoice.model.Invoice;
import com.dileep.ailearning.invoice.model.LineItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Module 6: the scoring logic — pure, deterministic, no model needed. */
class InvoiceEvaluatorTest {

    private final InvoiceEvaluator evaluator = new InvoiceEvaluator();

    private static LineItem item(String desc, String total) {
        return new LineItem(desc, null, null, null, null, total == null ? null : new BigDecimal(total));
    }

    private static Invoice invoice(String number, String vendor, String currency,
                                   String total, List<LineItem> items) {
        return new Invoice(number, "2025-01-01", vendor, null, null, null, currency,
                items, null, null, total == null ? null : new BigDecimal(total));
    }

    @Test
    void perfectMatchScoresFull() {
        Invoice gold = invoice("INV-1", "Acme", "INR", "100.00", List.of(item("Widget", "100.00")));

        ExampleScore score = evaluator.score("perfect", gold, gold);

        assertThat(score.fieldAccuracy()).isEqualTo(1.0);
        assertThat(score.lineItemF1()).isEqualTo(1.0);
        assertThat(score.mismatchedFields()).isEmpty();
    }

    @Test
    void numberFormattingDifferencesStillMatch() {
        Invoice gold = invoice("INV-1", "Acme", "INR", "100.00", List.of(item("Widget", "100.00")));
        Invoice pred = invoice("INV-1", "Acme", "INR", "100", List.of(item("Widget", "100")));

        ExampleScore score = evaluator.score("nums", gold, pred);

        assertThat(score.fieldAccuracy()).isEqualTo(1.0); // 100 == 100.00
        assertThat(score.mismatchedFields()).isEmpty();
    }

    @Test
    void wrongScalarFieldIsCounted() {
        Invoice gold = invoice("INV-1", "Acme Ltd", "INR", "100.00", List.of(item("Widget", "100.00")));
        Invoice pred = invoice("INV-1", "Globex Ltd", "INR", "100.00", List.of(item("Widget", "100.00")));

        ExampleScore score = evaluator.score("wrong-vendor", gold, pred);

        assertThat(score.mismatchedFields()).containsExactly("vendorName");
        assertThat(score.scalarFieldsMatched()).isEqualTo(9);
        assertThat(score.fieldAccuracy()).isCloseTo(0.9, within(1e-9));
    }

    @Test
    void nullVersusValueIsAMismatch() {
        Invoice gold = invoice("INV-1", "Acme", "INR", "100.00", List.of(item("Widget", "100.00")));
        Invoice pred = invoice("INV-1", "Acme", null, "100.00", List.of(item("Widget", "100.00")));

        ExampleScore score = evaluator.score("null-currency", gold, pred);

        assertThat(score.mismatchedFields()).containsExactly("currency");
    }

    @Test
    void lineItemPrecisionAndRecall() {
        Invoice gold = invoice("INV-1", "Acme", "INR", "300.00",
                List.of(item("A", "100.00"), item("B", "200.00")));
        // predicted finds only item A → precision 1/1 = 1.0, recall 1/2 = 0.5
        Invoice pred = invoice("INV-1", "Acme", "INR", "300.00", List.of(item("A", "100.00")));

        ExampleScore score = evaluator.score("partial-items", gold, pred);

        assertThat(score.lineItemPrecision()).isEqualTo(1.0);
        assertThat(score.lineItemRecall()).isEqualTo(0.5);
        assertThat(score.lineItemF1()).isCloseTo(0.6667, within(1e-3));
    }

    @Test
    void aggregateComputesPerFieldAccuracy() {
        Invoice gold = invoice("INV-1", "Acme", "INR", "100.00", List.of(item("Widget", "100.00")));
        ExampleScore clean = evaluator.score("a", gold, gold);
        ExampleScore wrongVendor = evaluator.score("b", gold,
                invoice("INV-1", "WRONG", "INR", "100.00", List.of(item("Widget", "100.00"))));

        EvaluationReport report = evaluator.aggregate(List.of(clean, wrongVendor));

        assertThat(report.examples()).isEqualTo(2);
        assertThat(report.perFieldAccuracy().get("vendorName")).isEqualTo(0.5); // 1 of 2 correct
        assertThat(report.perFieldAccuracy().get("invoiceNumber")).isEqualTo(1.0);
        assertThat(report.meanFieldAccuracy()).isCloseTo(0.95, within(1e-9)); // (1.0 + 0.9) / 2
    }
}
