package com.dileep.ailearning.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Module 4: pin down the MMR re-ranking math — the whole point is that it favours
 * diversity over near-duplicates, which a pure top-k by similarity would not.
 */
class MmrTest {

    /** Tiny test item: an id, a relevance score, and an embedding. */
    record Doc(String id, double relevance, float[] embedding) {
    }

    @Test
    void prefersDiversityOverNearDuplicates() {
        // A is most relevant. A2 is a near-duplicate of A (slightly less relevant).
        // B is less relevant but points in a totally different direction.
        Doc a = new Doc("A", 0.90, new float[]{1, 0, 0});
        Doc a2 = new Doc("A2", 0.85, new float[]{0.99f, 0.01f, 0});
        Doc b = new Doc("B", 0.70, new float[]{0, 1, 0});
        List<Doc> candidates = List.of(a, a2, b);

        List<Doc> ranked = Mmr.rerank(candidates, Doc::relevance, Doc::embedding, 2, 0.6);

        // MMR picks A first (most relevant), then B for diversity — NOT the near-duplicate A2.
        assertThat(ranked).extracting(Doc::id).containsExactly("A", "B");
    }

    @Test
    void pureRelevanceLambdaKeepsNearDuplicate() {
        Doc a = new Doc("A", 0.90, new float[]{1, 0, 0});
        Doc a2 = new Doc("A2", 0.85, new float[]{0.99f, 0.01f, 0});
        Doc b = new Doc("B", 0.70, new float[]{0, 1, 0});

        // lambda = 1.0 → diversity term ignored → pure top-by-relevance → A then A2.
        List<Doc> ranked = Mmr.rerank(List.of(a, a2, b), Doc::relevance, Doc::embedding, 2, 1.0);

        assertThat(ranked).extracting(Doc::id).containsExactly("A", "A2");
    }

    @Test
    void returnsAllWhenKExceedsCandidates() {
        Doc a = new Doc("A", 0.9, new float[]{1, 0});
        Doc b = new Doc("B", 0.8, new float[]{0, 1});
        assertThat(Mmr.rerank(List.of(a, b), Doc::relevance, Doc::embedding, 10, 0.5)).hasSize(2);
    }

    @Test
    void emptyCandidatesYieldEmpty() {
        assertThat(Mmr.rerank(List.<Doc>of(), Doc::relevance, Doc::embedding, 5, 0.5)).isEmpty();
    }

    @Test
    void cosineSimilarityBasics() {
        assertThat(Mmr.cosineSimilarity(new float[]{1, 0}, new float[]{1, 0})).isCloseTo(1.0, within(1e-9));
        assertThat(Mmr.cosineSimilarity(new float[]{1, 0}, new float[]{0, 1})).isCloseTo(0.0, within(1e-9));
        assertThat(Mmr.cosineSimilarity(new float[]{1, 0}, new float[]{0, 0})).isZero(); // zero vector
    }
}
