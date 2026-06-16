package com.dileep.ailearning.guardrail;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects and masks personally-identifiable / sensitive information in text (Module 6).
 *
 * <p>Why it matters in an LLM pipeline: you don't want PII (and definitely not card
 * numbers) sitting in <b>logs</b>, traces, or prompt caches — and if you ever
 * escalate from a local model to a hosted API, you may need to redact before the
 * data leaves your boundary. Redact-before-log and redact-before-egress are the two
 * key control points.
 *
 * <p>Patterns are ordered so more specific items (GSTIN, card) are masked before
 * overlapping general ones (PAN, phone). Pure logic — no model needed.
 */
@Component
public class PiiRedactor {

    public enum PiiType {EMAIL, GSTIN, PAN, AADHAAR, CREDIT_CARD, PHONE}

    /** Order matters: specific/longer patterns first so they win over overlaps. */
    private static final List<Rule> RULES = List.of(
            new Rule(PiiType.EMAIL, Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
            new Rule(PiiType.GSTIN, Pattern.compile("\\b[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]\\b")),
            new Rule(PiiType.CREDIT_CARD, Pattern.compile("\\b(?:[0-9]{4}[ -]?){3}[0-9]{4}\\b")),
            new Rule(PiiType.AADHAAR, Pattern.compile("\\b[2-9][0-9]{3}\\s?[0-9]{4}\\s?[0-9]{4}\\b")),
            new Rule(PiiType.PAN, Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b")),
            new Rule(PiiType.PHONE, Pattern.compile("(?<!\\d)(?:\\+?91[\\s-]?)?[6-9][0-9]{9}(?!\\d)")));

    /**
     * Replace any PII found with type tags like {@code [REDACTED_EMAIL]}.
     *
     * @return the redacted text plus a count of what was masked, by type
     */
    public RedactionResult redact(String text) {
        if (text == null || text.isBlank()) {
            return new RedactionResult(text == null ? "" : text, Map.of());
        }
        String working = text;
        Map<PiiType, Integer> counts = new LinkedHashMap<>();
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(working);
            int n = 0;
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                n++;
                m.appendReplacement(sb, "[REDACTED_" + rule.type() + "]");
            }
            m.appendTail(sb);
            if (n > 0) {
                counts.put(rule.type(), n);
                working = sb.toString();
            }
        }
        return new RedactionResult(working, counts);
    }

    /** True if any PII was detected. */
    public boolean containsPii(String text) {
        return !redact(text).counts().isEmpty();
    }

    public List<PiiType> detectedTypes(String text) {
        return List.copyOf(redact(text).counts().keySet());
    }

    private record Rule(PiiType type, Pattern pattern) {
    }

    /**
     * @param redactedText the input with PII masked
     * @param counts       how many of each PII type were found
     */
    public record RedactionResult(String redactedText, Map<PiiType, Integer> counts) {
    }
}
