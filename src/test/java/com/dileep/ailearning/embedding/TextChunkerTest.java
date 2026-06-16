package com.dileep.ailearning.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Module 3: pin down chunking behaviour — the correctness of retrieval starts here.
 */
class TextChunkerTest {

    @Test
    void chunksWithNoOverlap() {
        List<String> chunks = TextChunker.chunk("abcdefghij", 5, 0);
        assertThat(chunks).containsExactly("abcde", "fghij");
    }

    @Test
    void chunksWithOverlap() {
        // "abcde", then step = 5-2 = 3, so next starts at 3: "defgh", then 6: "fghij"
        List<String> chunks = TextChunker.chunk("abcdefghij", 5, 2);
        assertThat(chunks).containsExactly("abcde", "defgh", "ghij");
    }

    @Test
    void lastChunkCanBeShorter() {
        List<String> chunks = TextChunker.chunk("abcdefg", 5, 0);
        assertThat(chunks).containsExactly("abcde", "fg");
    }

    @Test
    void singleChunkIfTextShorterThanChunkSize() {
        List<String> chunks = TextChunker.chunk("abc", 10, 0);
        assertThat(chunks).containsExactly("abc");
    }

    @Test
    void emptyInputReturnsEmptyList() {
        assertThat(TextChunker.chunk("", 5, 0)).isEmpty();
        assertThat(TextChunker.chunk(null, 5, 0)).isEmpty();
        assertThat(TextChunker.chunk("   ", 5, 0)).isEmpty();
    }

    @Test
    void rejectsInvalidParams() {
        assertThatThrownBy(() -> TextChunker.chunk("abc", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextChunker.chunk("abc", 5, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextChunker.chunk("abc", 5, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
