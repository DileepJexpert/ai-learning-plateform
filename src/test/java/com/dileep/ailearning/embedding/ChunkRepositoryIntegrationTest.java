package com.dileep.ailearning.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module 3 integration test: real pgvector in Docker Compose. Verifies that:
 * <ol>
 *   <li>We can insert a vector and read it back via cosine similarity</li>
 *   <li>Cosine-similarity ranking works (most-similar chunk sorts first)</li>
 *   <li>Delete by doc_name works</li>
 * </ol>
 *
 * <p>Start pgvector first: {@code docker compose up -d}, then {@code ./mvnw test}.
 */
@SpringBootTest
@ActiveProfiles("integration")
class ChunkRepositoryIntegrationTest {

    @Autowired
    private ChunkRepository repo;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void cleanTable() {
        jdbc.sql("DELETE FROM document_chunks").update();
    }

    @Test
    void saveAndSearchByCosine() {
        // Two chunks with distinct embeddings (768-dim for nomic-embed-text).
        // Chunk A is "up" in dim 0, chunk B in dim 1.
        float[] vecA = unitVector(768, 0);
        float[] vecB = unitVector(768, 1);

        repo.save(DocumentChunk.of("doc-1", 0, "About apples", vecA));
        repo.save(DocumentChunk.of("doc-1", 1, "About bananas", vecB));

        assertThat(repo.count()).isEqualTo(2);

        // Search with a query vector identical to A
        List<ChunkRepository.SearchHit> hits = repo.searchSimilar(vecA, 2);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).content()).isEqualTo("About apples"); // most similar to vecA
        assertThat(hits.get(0).similarity()).isGreaterThan(hits.get(1).similarity());
    }

    @Test
    void deleteByDocName() {
        repo.save(DocumentChunk.of("doc-x", 0, "keep", unitVector(768, 0)));
        repo.save(DocumentChunk.of("doc-y", 0, "delete", unitVector(768, 1)));

        int deleted = repo.deleteByDocName("doc-y");

        assertThat(deleted).isEqualTo(1);
        assertThat(repo.count()).isEqualTo(1);
    }

    @Test
    void searchReturnsEmptyWhenNoData() {
        List<ChunkRepository.SearchHit> hits = repo.searchSimilar(unitVector(768, 0), 5);
        assertThat(hits).isEmpty();
    }

    @Test
    void searchCandidatesRoundTripsTheEmbedding() {
        // The candidate query reads the vector column BACK from Postgres — verify it
        // parses into a correct float[] (this is what MMR re-ranking depends on).
        float[] vec = unitVector(768, 3);
        repo.save(DocumentChunk.of("doc-1", 0, "hello", vec));

        List<ChunkRepository.Candidate> candidates = repo.searchCandidates(vec, 1);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).embedding()).hasSize(768);
        assertThat(candidates.get(0).embedding()[3]).isEqualTo(1.0f);
        assertThat(candidates.get(0).similarity()).isCloseTo(1.0, org.assertj.core.api.Assertions.within(1e-5));
    }

    /** Creates a unit vector with 1.0 at the given index and 0.0 elsewhere. */
    private static float[] unitVector(int dims, int hotIndex) {
        float[] vec = new float[dims];
        vec[hotIndex] = 1.0f;
        return vec;
    }
}
