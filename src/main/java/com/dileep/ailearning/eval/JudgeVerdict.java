package com.dileep.ailearning.eval;

/**
 * An LLM-judge's verdict on a fuzzy output (Module 6).
 *
 * @param score  1–5 quality score (5 = best)
 * @param pass   whether the score met the configured pass threshold
 * @param reason the judge's short justification
 */
public record JudgeVerdict(int score, boolean pass, String reason) {
}
