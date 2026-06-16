package com.dileep.ailearning.eval;

import com.dileep.ailearning.invoice.ExtractionResult;
import com.dileep.ailearning.invoice.InvoiceExtractionService;
import com.dileep.ailearning.invoice.model.Invoice;
import com.dileep.ailearning.invoice.model.LineItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Module 6: the eval orchestration. The extractor is mocked (no Ollama), so we test
 * that the harness runs each example and aggregates correctly — and that the bundled
 * dataset parses.
 */
class EvaluationServiceTest {

    private final InvoiceExtractionService extractor = mock(InvoiceExtractionService.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final EvaluationService service =
            new EvaluationService(extractor, new InvoiceEvaluator(), objectMapper);

    private static Invoice invoice(String number, String total) {
        return new Invoice(number, "2025-01-01", "Acme", null, null, null, "INR",
                List.of(new LineItem("Widget", null, null, null, null, new BigDecimal(total))),
                null, null, new BigDecimal(total));
    }

    private static ExtractionResult result(Invoice invoice) {
        return new ExtractionResult(invoice, "test-model", 1, 1L, null, null);
    }

    @Test
    void perfectExtractionScoresFullAccuracy() {
        Invoice gold = invoice("INV-1", "100.00");
        var dataset = List.of(new LabeledInvoice("ex1", "some text", gold));
        when(extractor.extract(eq("some text"))).thenReturn(result(gold));

        EvaluationReport report = service.evaluate(dataset);

        assertThat(report.examples()).isEqualTo(1);
        assertThat(report.meanFieldAccuracy()).isEqualTo(1.0);
        assertThat(report.meanLineItemF1()).isEqualTo(1.0);
    }

    @Test
    void imperfectExtractionLowersAccuracyAndShowsWeakField() {
        Invoice gold = invoice("INV-1", "100.00");
        Invoice predicted = invoice("INV-1", "999.00"); // wrong total
        var dataset = List.of(new LabeledInvoice("ex1", "text", gold));
        when(extractor.extract(eq("text"))).thenReturn(result(predicted));

        EvaluationReport report = service.evaluate(dataset);

        assertThat(report.meanFieldAccuracy()).isLessThan(1.0);
        assertThat(report.perFieldAccuracy().get("totalAmount")).isEqualTo(0.0);
        assertThat(report.scores().get(0).mismatchedFields()).contains("totalAmount");
    }

    @Test
    void bundledDatasetLoadsAndParses() {
        List<LabeledInvoice> dataset = service.loadDataset("eval/invoice-evalset.json");

        assertThat(dataset).hasSize(3);
        assertThat(dataset).extracting(LabeledInvoice::name)
                .containsExactly("clean-two-line", "missing-fields", "single-item-usd");
        // spot-check that the gold Invoice deserialized correctly
        assertThat(dataset.get(0).expected().lineItems()).hasSize(2);
        assertThat(dataset.get(0).expected().totalAmount()).isEqualByComparingTo("3422.00");
    }
}
