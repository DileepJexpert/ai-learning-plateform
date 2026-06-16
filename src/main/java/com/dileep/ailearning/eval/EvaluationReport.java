package com.dileep.ailearning.eval;

import java.util.List;
import java.util.Map;

/**
 * Aggregate results over an evaluation run (Module 6).
 *
 * @param examples          number of examples evaluated
 * @param meanFieldAccuracy average scalar-field accuracy across examples (0..1)
 * @param meanLineItemF1    average line-item F1 across examples (0..1)
 * @param perFieldAccuracy  per-field accuracy — which fields the model gets right/wrong
 *                          (e.g. "vendorGstin" might be the weakest)
 * @param scores            the individual example scores
 */
public record EvaluationReport(
        int examples,
        double meanFieldAccuracy,
        double meanLineItemF1,
        Map<String, Double> perFieldAccuracy,
        List<ExampleScore> scores
) {
}
