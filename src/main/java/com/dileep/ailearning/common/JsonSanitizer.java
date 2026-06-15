package com.dileep.ailearning.common;

/**
 * Best-effort extraction of a single JSON object from a model's raw reply.
 *
 * <p>With {@code format: "json"} Ollama already returns clean JSON, so this is a
 * safety net for weaker models / free-form mode that wrap their answer in
 * ```json fences``` or add a stray "Here is the JSON:" preamble. We pull out the
 * first balanced {@code {...}} block, ignoring braces that appear inside strings.
 *
 * <p>This is intentionally a static utility with no Spring dependency so it is
 * trivial to unit-test.
 */
public final class JsonSanitizer {

    private JsonSanitizer() {
    }

    /**
     * @param raw the model's reply (may include fences / prose)
     * @return the first balanced JSON object substring, or the trimmed input if
     *         no balanced object is found (let the JSON parser produce the real error)
     */
    public static String extractJsonObject(String raw) {
        if (raw == null) {
            return "";
        }
        String text = stripCodeFences(raw.trim());

        int start = text.indexOf('{');
        if (start < 0) {
            return text;
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> depth++;
                case '}' -> {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
                default -> { /* ignore */ }
            }
        }
        // Unbalanced — return from the first brace and let Jackson report the problem.
        return text.substring(start);
    }

    private static String stripCodeFences(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        // Drop the opening fence line (``` or ```json) ...
        int firstNewline = text.indexOf('\n');
        String body = firstNewline < 0 ? text.substring(3) : text.substring(firstNewline + 1);
        // ... and a trailing closing fence if present.
        int closing = body.lastIndexOf("```");
        if (closing >= 0) {
            body = body.substring(0, closing);
        }
        return body.trim();
    }
}
