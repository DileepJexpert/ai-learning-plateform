package com.dileep.ailearning.compliance.search;

import java.util.*;

/**
 * Implementation of Reciprocal Rank Fusion (RRF).
 *
 * <p>In enterprise search, neither dense vector embeddings nor keyword/BM25 search
 * is sufficient alone:
 * <ul>
 *   <li>Dense vectors miss exact circular numbers, clause IDs ("Section 4.2(a)"), and acronyms ("LCR", "NSFR").</li>
 *   <li>Keyword search misses conceptual semantics ("capital buffer requirements" vs "adequacy limits").</li>
 * </ul>
 *
 * <p>RRF merges independent ranked lists without requiring score normalization
 * (which is problematic because cosine similarity [0..1] and BM25 scores [0..&infin;]
 * have different distributions):
 * <pre>
 *   RRF_Score(d) = &sum; [ 1 / (k + rank(d)) ]
 * </pre>
 * where {@code k} is a smoothing constant (standard default is 60).
 */
public final class ReciprocalRankFusion {

    public static final int DEFAULT_RRF_K = 60;

    private ReciprocalRankFusion() {
    }

    public record ScoredItem<T>(T item, double score) {
    }

    public record FusedResult<T>(T item, double rrfScore, Double denseScore, Double sparseScore) {
    }

    /**
     * Merge dense and sparse ranked lists using Reciprocal Rank Fusion.
     *
     * @param denseRanked  ordered list from dense vector search (highest similarity first)
     * @param sparseRanked ordered list from sparse/BM25 search (highest BM25 score first)
     * @param idExtractor  function mapping an item to a unique key/id
     * @param rrfK         smoothing parameter (typically 60)
     * @param topK         maximum number of fused results to return
     * @param <T>          item type
     * @param <K>          key type
     * @return fused and re-ranked list sorted by descending RRF score
     */
    public static <T, K> List<FusedResult<T>> fuse(
            List<ScoredItem<T>> denseRanked,
            List<ScoredItem<T>> sparseRanked,
            java.util.function.Function<T, K> idExtractor,
            int rrfK,
            int topK) {

        Map<K, T> itemsByKey = new HashMap<>();
        Map<K, Double> rrfScores = new HashMap<>();
        Map<K, Double> denseScores = new HashMap<>();
        Map<K, Double> sparseScores = new HashMap<>();

        // Process Dense Rankings (1-based rank)
        for (int i = 0; i < denseRanked.size(); i++) {
            ScoredItem<T> scored = denseRanked.get(i);
            K key = idExtractor.apply(scored.item());
            itemsByKey.putIfAbsent(key, scored.item());
            denseScores.put(key, scored.score());

            int rank = i + 1;
            double scoreContribution = 1.0 / (rrfK + rank);
            rrfScores.merge(key, scoreContribution, Double::sum);
        }

        // Process Sparse Rankings (1-based rank)
        for (int i = 0; i < sparseRanked.size(); i++) {
            ScoredItem<T> scored = sparseRanked.get(i);
            K key = idExtractor.apply(scored.item());
            itemsByKey.putIfAbsent(key, scored.item());
            sparseScores.put(key, scored.score());

            int rank = i + 1;
            double scoreContribution = 1.0 / (rrfK + rank);
            rrfScores.merge(key, scoreContribution, Double::sum);
        }

        // Sort by RRF score descending
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<K, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    K key = entry.getKey();
                    T item = itemsByKey.get(key);
                    return new FusedResult<>(
                            item,
                            entry.getValue(),
                            denseScores.get(key),
                            sparseScores.get(key)
                    );
                })
                .toList();
    }
}
