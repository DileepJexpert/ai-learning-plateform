package com.dileep.ailearning.eval;

import com.dileep.ailearning.invoice.model.Invoice;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One labelled example in the evaluation set (Module 6): raw invoice text plus the
 * known-correct ("gold") extraction. The eval harness runs the extractor on
 * {@code text} and scores the result against {@code expected}.
 *
 * @param name     a label for reporting (e.g. "missing-fields")
 * @param text     the raw invoice text to extract
 * @param expected the gold-standard Invoice
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LabeledInvoice(String name, String text, Invoice expected) {
}
