package com.dileep.ailearning.embedding;

import com.pgvector.PGvector;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * pgvector-backed storage for document chunk embeddings (Module 3).
 *
 * <p>Uses {@link JdbcClient} + raw SQL so you can see exactly what goes to
 * Postgres — no ORM abstraction hiding the vector operations. The key operator
 * is {@code <=>} (cosine distance): lower distance = more similar.
 */
@Repository
public class ChunkRepository {

    private final JdbcClient jdbc;

    public ChunkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Insert a chunk with its embedding vector. */
    public void save(DocumentChunk chunk) {
        jdbc.sql("""
                INSERT INTO document_chunks (doc_name, chunk_index, content, embedding)
                VALUES (?, ?, ?, ?::vector)
                """)
                .param(chunk.docName())
                .param(chunk.chunkIndex())
                .param(chunk.content())
                .param(new PGvector(chunk.embedding()).toString())
                .update();
    }

    /** Insert a batch of chunks in one go. */
    public void saveAll(List<DocumentChunk> chunks) {
        for (DocumentChunk chunk : chunks) {
            save(chunk);
        }
    }

    /**
     * Cosine-similarity search: find the {@code k} chunks most similar to the
     * query embedding.
     *
     * <p>pgvector's {@code <=>} operator computes <b>cosine distance</b>
     * (= 1 − cosine similarity). We ORDER BY it ascending, so the most-similar
     * chunks come first. The column alias {@code similarity} is
     * {@code 1 − distance}, i.e. the familiar 0..1 cosine similarity score.
     */
    public List<SearchHit> searchSimilar(float[] queryEmbedding, int k) {
        return jdbc.sql("""
                SELECT id, doc_name, chunk_index, content,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM document_chunks
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """)
                .param(new PGvector(queryEmbedding).toString())
                .param(new PGvector(queryEmbedding).toString())
                .param(k)
                .query(this::mapSearchHit)
                .list();
    }

    /**
     * Like {@link #searchSimilar} but also returns each chunk's embedding vector,
     * so a re-ranker (e.g. MMR, Module 4) can compute chunk-to-chunk similarity.
     * Used to fetch a larger candidate pool that is then re-ranked down to top-k.
     */
    public List<Candidate> searchCandidates(float[] queryEmbedding, int k) {
        return jdbc.sql("""
                SELECT id, doc_name, chunk_index, content, embedding,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM document_chunks
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """)
                .param(new PGvector(queryEmbedding).toString())
                .param(new PGvector(queryEmbedding).toString())
                .param(k)
                .query(this::mapCandidate)
                .list();
    }

    /** Delete all chunks for a given document (e.g. before re-ingesting). */
    public int deleteByDocName(String docName) {
        return jdbc.sql("DELETE FROM document_chunks WHERE doc_name = ?")
                .param(docName)
                .update();
    }

    /** Count total chunks (useful for logging after ingest). */
    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM document_chunks")
                .query(Long.class)
                .single();
    }

    private SearchHit mapSearchHit(ResultSet rs, int rowNum) throws SQLException {
        return new SearchHit(
                rs.getLong("id"),
                rs.getString("doc_name"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getDouble("similarity"));
    }

    private Candidate mapCandidate(ResultSet rs, int rowNum) throws SQLException {
        // The vector column comes back as a string like "[0.1,0.2,...]"; parse it via PGvector.
        float[] embedding = new PGvector(rs.getString("embedding")).toArray();
        return new Candidate(
                rs.getLong("id"),
                rs.getString("doc_name"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getDouble("similarity"),
                embedding);
    }

    /**
     * A chunk that matched a similarity search, with its cosine-similarity score.
     *
     * @param similarity 1.0 = identical, 0.0 = orthogonal (no relation)
     */
    public record SearchHit(long id, String docName, int chunkIndex, String content, double similarity) {
    }

    /** A search hit plus its embedding vector — input to re-ranking (Module 4). */
    public record Candidate(long id, String docName, int chunkIndex, String content,
                            double similarity, float[] embedding) {
    }
}
