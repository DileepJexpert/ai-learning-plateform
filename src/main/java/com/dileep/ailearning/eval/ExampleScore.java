package com.dileep.ailearning.eval;

import java.util.List;

/**
 * The score for a single evaluated example (Module 6).
 *
 * @param name               the example's label
 * @param scalarFieldCount    how many scalar fields were compared
 * @param scalarFieldsMatched how many matched the gold value
 * @param fieldAccuracy       matched / total scalar fields (0..1)
 * @param lineItemPrecision   of the predicted line items, fraction that were correct
 * @param lineItemRecall      of the expected line items, fraction that were found
 * @param lineItemF1          harmonic mean of precision and recall
 * @param mismatchedFields    which scalar fields were wrong (great for spotting weak spots)
 */
public record ExampleScore(
        String name,
        int scalarFieldCount,
        int scalarFieldsMatched,
        double fieldAccuracy,
        double lineItemPrecision,
        double lineItemRecall,
        double lineItemF1,
        List<String> mismatchedFields
) {
}
