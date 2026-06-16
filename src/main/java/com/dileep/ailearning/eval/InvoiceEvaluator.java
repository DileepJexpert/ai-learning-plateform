package com.dileep.ailearning.eval;

import com.dileep.ailearning.invoice.model.Invoice;
import com.dileep.ailearning.invoice.model.LineItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores a predicted {@link Invoice} against the gold one (Module 6).
 *
 * <p>This is the heart of "how do you know it works?": instead of eyeballing
 * outputs, we measure them. Two metrics:
 * <ul>
 *   <li><b>Scalar-field accuracy</b> — of the flat fields (number, dates, names,
 *       GSTINs, totals), how many exactly match the gold value.</li>
 *   <li><b>Line-item precision/recall/F1</b> — line items are a set, so we use
 *       precision (of predicted items, how many are correct) and recall (of
 *       expected items, how many were found). F1 combines them.</li>
 * </ul>
 *
 * <p>Pure logic, no model needed — so it's fully unit-testable and deterministic.
 */
@Component
public class InvoiceEvaluator {

    /** The flat fields we score for exact-match accuracy. */
    static final List<String> SCALAR_FIELDS = List.of(
            "invoiceNumber", "invoiceDate", "vendorName", "vendorGstin", "buyerName",
            "buyerGstin", "currency", "subtotal", "taxAmount", "totalAmount");

    public ExampleScore score(String name, Invoice expected, Invoice predicted) {
        List<String> mismatches = new ArrayList<>();
        for (String field : SCALAR_FIELDS) {
            if (!scalarMatches(valueOf(expected, field), valueOf(predicted, field))) {
                mismatches.add(field);
            }
        }
        int matched = SCALAR_FIELDS.size() - mismatches.size();
        double fieldAccuracy = (double) matched / SCALAR_FIELDS.size();

        LineItemScore li = scoreLineItems(expected.lineItems(), predicted.lineItems());

        return new ExampleScore(name, SCALAR_FIELDS.size(), matched, fieldAccuracy,
                li.precision(), li.recall(), li.f1(), List.copyOf(mismatches));
    }

    /** Aggregate a list of example scores into an overall report. */
    public EvaluationReport aggregate(List<ExampleScore> scores) {
        int n = scores.size();
        double meanFieldAccuracy = scores.stream().mapToDouble(ExampleScore::fieldAccuracy).average().orElse(0);
        double meanF1 = scores.stream().mapToDouble(ExampleScore::lineItemF1).average().orElse(0);

        Map<String, Double> perField = new LinkedHashMap<>();
        for (String field : SCALAR_FIELDS) {
            long ok = scores.stream().filter(s -> !s.mismatchedFields().contains(field)).count();
            perField.put(field, n == 0 ? 0.0 : (double) ok / n);
        }
        return new EvaluationReport(n, meanFieldAccuracy, meanF1, perField, scores);
    }

    // --- scalar comparison ---------------------------------------------------

    private static boolean scalarMatches(Object expected, Object predicted) {
        if (expected == null || predicted == null) {
            return expected == predicted; // both null = match; one null = mismatch
        }
        if (expected instanceof BigDecimal a && predicted instanceof BigDecimal b) {
            return a.compareTo(b) == 0; // 2500 == 2500.00
        }
        // Text fields: trim + case-insensitive (robust to trivial formatting differences).
        return expected.toString().trim().equalsIgnoreCase(predicted.toString().trim());
    }

    private static Object valueOf(Invoice inv, String field) {
        return switch (field) {
            case "invoiceNumber" -> inv.invoiceNumber();
            case "invoiceDate" -> inv.invoiceDate();
            case "vendorName" -> inv.vendorName();
            case "vendorGstin" -> inv.vendorGstin();
            case "buyerName" -> inv.buyerName();
            case "buyerGstin" -> inv.buyerGstin();
            case "currency" -> inv.currency();
            case "subtotal" -> inv.subtotal();
            case "taxAmount" -> inv.taxAmount();
            case "totalAmount" -> inv.totalAmount();
            default -> throw new IllegalArgumentException("unknown field: " + field);
        };
    }

    // --- line-item comparison ------------------------------------------------

    private LineItemScore scoreLineItems(List<LineItem> expected, List<LineItem> predicted) {
        int exp = expected == null ? 0 : expected.size();
        int pred = predicted == null ? 0 : predicted.size();
        if (exp == 0 && pred == 0) {
            return new LineItemScore(1.0, 1.0, 1.0); // nothing to find, nothing wrong
        }

        // Greedy match: each expected item matches at most one not-yet-used predicted item.
        boolean[] used = new boolean[pred];
        int matches = 0;
        for (int e = 0; e < exp; e++) {
            for (int p = 0; p < pred; p++) {
                if (!used[p] && lineItemsMatch(expected.get(e), predicted.get(p))) {
                    used[p] = true;
                    matches++;
                    break;
                }
            }
        }

        double precision = pred == 0 ? 0.0 : (double) matches / pred;
        double recall = exp == 0 ? 0.0 : (double) matches / exp;
        double f1 = (precision + recall == 0) ? 0.0 : 2 * precision * recall / (precision + recall);
        return new LineItemScore(precision, recall, f1);
    }

    /** Two line items match if their description (trim/case-insensitive) and lineTotal agree. */
    private static boolean lineItemsMatch(LineItem a, LineItem b) {
        boolean descMatch = scalarMatches(a.description(), b.description());
        boolean totalMatch = scalarMatches(a.lineTotal(), b.lineTotal());
        return descMatch && totalMatch;
    }

    private record LineItemScore(double precision, double recall, double f1) {
    }
}
