package com.dileep.ailearning.compliance.search;

import com.dileep.ailearning.compliance.search.ReciprocalRankFusion.FusedResult;
import com.dileep.ailearning.compliance.search.ReciprocalRankFusion.ScoredItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReciprocalRankFusionTest {

    private record TestDoc(String id, String title) {
    }

    @Test
    void fusesDenseAndSparseRanksWithDualMatchDominance() {
        TestDoc docA = new TestDoc("DOC-A", "High Risk KYC");
        TestDoc docB = new TestDoc("DOC-B", "LCR Level 1 HQLA");
        TestDoc docC = new TestDoc("DOC-C", "General Treasury Guidelines");

        // Dense results: Doc B ranks #1, Doc A ranks #2
        List<ScoredItem<TestDoc>> denseList = List.of(
                new ScoredItem<>(docB, 0.95),
                new ScoredItem<>(docA, 0.88)
        );

        // Sparse results: Doc A ranks #1, Doc C ranks #2
        List<ScoredItem<TestDoc>> sparseList = List.of(
                new ScoredItem<>(docA, 4.5),
                new ScoredItem<>(docC, 3.2)
        );

        List<FusedResult<TestDoc>> fused = ReciprocalRankFusion.fuse(
                denseList,
                sparseList,
                TestDoc::id,
                60,
                5
        );

        assertThat(fused).hasSize(3);

        // Doc A appears in BOTH lists (Rank 2 in dense, Rank 1 in sparse):
        // RRF(A) = 1/(60+2) + 1/(60+1) = 1/62 + 1/61 = 0.016129 + 0.016393 = ~0.03252
        // Doc B appears only in dense (Rank 1):
        // RRF(B) = 1/(60+1) = ~0.01639
        // Doc C appears only in sparse (Rank 2):
        // RRF(C) = 1/(60+2) = ~0.01612
        assertThat(fused.get(0).item().id()).isEqualTo("DOC-A");
        assertThat(fused.get(0).denseScore()).isEqualTo(0.88);
        assertThat(fused.get(0).sparseScore()).isEqualTo(4.5);
        assertThat(fused.get(0).rrfScore()).isGreaterThan(fused.get(1).rrfScore());

        assertThat(fused.get(1).item().id()).isEqualTo("DOC-B");
        assertThat(fused.get(2).item().id()).isEqualTo("DOC-C");
    }

    @Test
    void respectsTopKLimit() {
        TestDoc docA = new TestDoc("DOC-A", "A");
        TestDoc docB = new TestDoc("DOC-B", "B");
        TestDoc docC = new TestDoc("DOC-C", "C");

        List<ScoredItem<TestDoc>> dense = List.of(
                new ScoredItem<>(docA, 0.9),
                new ScoredItem<>(docB, 0.8),
                new ScoredItem<>(docC, 0.7)
        );

        List<FusedResult<TestDoc>> fused = ReciprocalRankFusion.fuse(
                dense, List.of(), TestDoc::id, 60, 2);

        assertThat(fused).hasSize(2);
        assertThat(fused.get(0).item().id()).isEqualTo("DOC-A");
        assertThat(fused.get(1).item().id()).isEqualTo("DOC-B");
    }
}
