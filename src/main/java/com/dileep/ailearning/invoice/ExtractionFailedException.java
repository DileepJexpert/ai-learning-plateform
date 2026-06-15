package com.dileep.ailearning.invoice;

import java.util.List;

/**
 * Thrown when the model could not produce valid, schema-correct JSON even after
 * the allowed retries. We keep the raw model output and the specific problems so
 * the API can return a useful 422 (and so you can see <i>why</i> it failed).
 *
 * <p>This is the guardrail doing its job: bad output is rejected here, never
 * persisted.
 */
public class ExtractionFailedException extends RuntimeException {

    private final String rawModelOutput;
    private final List<String> problems;
    private final int attempts;

    public ExtractionFailedException(String message, String rawModelOutput,
                                     List<String> problems, int attempts) {
        super(message);
        this.rawModelOutput = rawModelOutput;
        this.problems = problems == null ? List.of() : List.copyOf(problems);
        this.attempts = attempts;
    }

    public String getRawModelOutput() {
        return rawModelOutput;
    }

    public List<String> getProblems() {
        return problems;
    }

    public int getAttempts() {
        return attempts;
    }
}
