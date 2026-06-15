package com.dileep.ailearning.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The safety net that rescues JSON from chatty/fenced model replies. */
class JsonSanitizerTest {

    @Test
    void passesCleanJsonThrough() {
        String json = "{\"a\":1,\"b\":\"x\"}";
        assertThat(JsonSanitizer.extractJsonObject(json)).isEqualTo(json);
    }

    @Test
    void stripsMarkdownCodeFences() {
        String raw = "```json\n{\"a\":1}\n```";
        assertThat(JsonSanitizer.extractJsonObject(raw)).isEqualTo("{\"a\":1}");
    }

    @Test
    void extractsObjectFromSurroundingProse() {
        String raw = "Sure, here you go:\n{\"a\":1}\nHope that helps!";
        assertThat(JsonSanitizer.extractJsonObject(raw)).isEqualTo("{\"a\":1}");
    }

    @Test
    void handlesNestedBraces() {
        String raw = "noise {\"a\":{\"b\":2}} trailing";
        assertThat(JsonSanitizer.extractJsonObject(raw)).isEqualTo("{\"a\":{\"b\":2}}");
    }

    @Test
    void ignoresBracesInsideStrings() {
        String raw = "{\"note\":\"a } brace in text\"}";
        assertThat(JsonSanitizer.extractJsonObject(raw)).isEqualTo(raw);
    }

    @Test
    void nullBecomesEmptyString() {
        assertThat(JsonSanitizer.extractJsonObject(null)).isEmpty();
    }
}
