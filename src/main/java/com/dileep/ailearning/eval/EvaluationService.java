package com.dileep.ailearning.eval;

import com.dileep.ailearning.invoice.InvoiceExtractionService;
import com.dileep.ailearning.invoice.model.Invoice;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the extraction eval (Module 6): for each labelled example, extract and score
 * against the gold answer, then aggregate.
 *
 * <p>This is the automated answer to "how do you know the feature works?" — a
 * number you can track over time and gate releases on, instead of spot-checking
 * outputs by hand.
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
    private static final String DEFAULT_DATASET = "eval/invoice-evalset.json";

    private final InvoiceExtractionService extractor;
    private final InvoiceEvaluator evaluator;
    private final ObjectMapper objectMapper;

    public EvaluationService(InvoiceExtractionService extractor, InvoiceEvaluator evaluator,
                             ObjectMapper objectMapper) {
        this.extractor = extractor;
        this.evaluator = evaluator;
        this.objectMapper = objectMapper;
    }

    /** Evaluate a provided dataset (used by tests with a small in-memory set). */
    public EvaluationReport evaluate(List<LabeledInvoice> dataset) {
        List<ExampleScore> scores = new ArrayList<>(dataset.size());
        for (LabeledInvoice example : dataset) {
            Invoice predicted = extractor.extract(example.text()).invoice();
            ExampleScore score = evaluator.score(example.name(), example.expected(), predicted);
            log.info("eval '{}': fieldAccuracy={} lineItemF1={} mismatches={}",
                    example.name(), score.fieldAccuracy(), score.lineItemF1(), score.mismatchedFields());
            scores.add(score);
        }
        EvaluationReport report = evaluator.aggregate(scores);
        log.info("eval complete: examples={} meanFieldAccuracy={} meanLineItemF1={}",
                report.examples(), report.meanFieldAccuracy(), report.meanLineItemF1());
        return report;
    }

    /** Evaluate the bundled default dataset. */
    public EvaluationReport evaluateDefaultDataset() {
        return evaluate(loadDataset(DEFAULT_DATASET));
    }

    public List<LabeledInvoice> loadDataset(String classpathLocation) {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            LabeledInvoice[] examples = objectMapper.readValue(in, LabeledInvoice[].class);
            return List.of(examples);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load eval dataset '" + classpathLocation + "'", e);
        }
    }
}
