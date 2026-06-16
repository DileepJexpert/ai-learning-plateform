package com.dileep.ailearning.embedding;

import java.time.Instant;

/**
 * A chunk of a document with its embedding vector, as stored in
 * {@code document_chunks} (Module 3).
 *
 * <p>This is a plain record, not a Spring Data entity — we write the SQL
 * ourselves in the repository so the pgvector operations stay explicit and
 * visible (the whole point of a learning project).
 */
public record DocumentChunk(
        Long id,
        String docName,
        int chunkIndex,
        String content,
        float[] embedding,
        Instant createdAt
) {

    /** Convenience: create a chunk that hasn't been persisted yet (no id/timestamp). */
    public static DocumentChunk of(String docName, int chunkIndex, String content, float[] embedding) {
        return new DocumentChunk(null, docName, chunkIndex, content, embedding, null);
    }
}
