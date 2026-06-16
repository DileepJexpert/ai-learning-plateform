package com.dileep.ailearning.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a document into overlapping chunks suitable for embedding (Module 3).
 *
 * <p><b>Why chunk?</b> Embedding models have a token limit and produce one vector
 * per input. A 50-page BRD must be broken into pieces; each piece gets its own
 * vector. At search time we retrieve the most-relevant <em>chunks</em>, not the
 * whole document.
 *
 * <p><b>Why overlap?</b> A sentence that straddles a chunk boundary gets split in
 * half — neither chunk carries the full meaning. Overlap ensures each boundary
 * region appears in two chunks, so at least one chunk has enough context.
 *
 * <p>Tuning tradeoffs:
 * <ul>
 *   <li>Smaller chunks → more precise retrieval, more vectors to store/search</li>
 *   <li>Larger chunks → more context per hit, fewer vectors, but noisier matches</li>
 *   <li>Larger overlap → better boundary coverage, but more redundancy</li>
 * </ul>
 *
 * <p>This is a character-based splitter. Production systems often split on sentence
 * or paragraph boundaries for cleaner semantics, but character-based is simple and
 * sufficient for learning the concept.
 */
public final class TextChunker {

    private TextChunker() {
    }

    /**
     * @param text      the full document text
     * @param chunkSize target size of each chunk in characters
     * @param overlap   characters of overlap between consecutive chunks
     * @return ordered list of chunks (empty list for blank input)
     */
    public static List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be > 0");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be >= 0 and < chunkSize");
        }

        List<String> chunks = new ArrayList<>();
        int step = chunkSize - overlap;
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
        }
        return List.copyOf(chunks);
    }
}
