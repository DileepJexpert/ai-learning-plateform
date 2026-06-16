package com.dileep.ailearning.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Maximal Marginal Relevance (MMR) re-ranking (Module 4).
 *
 * <p><b>Why re-rank at all?</b> Pure vector search returns the <i>most similar</i>
 * chunks — which are often near-duplicates of each other. Stuffing five copies of
 * the same fact wastes the context window and crowds out other relevant
 * information. MMR fixes this by balancing two goals:
 * <ul>
 *   <li><b>relevance</b> — how similar a chunk is to the query, and</li>
 *   <li><b>diversity</b> — how <i>different</i> a chunk is from ones already picked.</li>
 * </ul>
 *
 * <p>It picks greedily, each step maximising:
 * <pre>
 *   score(d) = λ · relevance(d) − (1 − λ) · max_{s ∈ selected} similarity(d, s)
 * </pre>
 * λ = 1 → pure relevance (no diversity); λ = 0 → pure diversity. Typical: 0.5–0.7.
 *
 * <p>It needs no extra model — it reuses the embeddings we already stored. We pass
 * relevance in (the cosine similarity pgvector already computed against the query),
 * so MMR only computes chunk-to-chunk similarities for the diversity term.
 */
public final class Mmr {

    private Mmr() {
    }

    /**
     * @param candidates  items to re-rank (already roughly ordered by relevance)
     * @param relevanceOf relevance score of an item w.r.t. the query (e.g. cosine similarity)
     * @param embeddingOf the item's embedding vector (for the diversity term)
     * @param k           how many to select
     * @param lambda      relevance/diversity balance in [0, 1]
     * @return up to {@code k} re-ranked items
     */
    public static <T> List<T> rerank(List<T> candidates,
                                     ToDoubleFunction<T> relevanceOf,
                                     Function<T, float[]> embeddingOf,
                                     int k,
                                     double lambda) {
        if (candidates.isEmpty() || k <= 0) {
            return List.of();
        }
        int target = Math.min(k, candidates.size());

        List<T> remaining = new ArrayList<>(candidates);
        List<T> selected = new ArrayList<>(target);
        List<float[]> selectedEmb = new ArrayList<>(target);

        while (selected.size() < target && !remaining.isEmpty()) {
            T best = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (T candidate : remaining) {
                double relevance = relevanceOf.applyAsDouble(candidate);
                double maxSimToSelected = 0.0;
                float[] emb = embeddingOf.apply(candidate);
                for (float[] s : selectedEmb) {
                    maxSimToSelected = Math.max(maxSimToSelected, cosineSimilarity(emb, s));
                }
                double score = lambda * relevance - (1.0 - lambda) * maxSimToSelected;
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }

            selected.add(best);
            selectedEmb.add(embeddingOf.apply(best));
            remaining.remove(best);
        }
        return selected;
    }

    /** Cosine similarity of two equal-length vectors; 0 if either is a zero vector. */
    static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("vector length mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
