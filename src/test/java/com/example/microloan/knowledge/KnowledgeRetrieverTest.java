package com.example.microloan.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeRetrieverTest {

    @Test
    void reciprocalRankFusionRewardsEvidenceReturnedByBothRetrievers() {
        KnowledgeEvidence shared = evidence("D1", "C1");
        KnowledgeEvidence fullTextOnly = evidence("D2", "C2");
        KnowledgeEvidence vectorOnly = evidence("D3", "C3");

        List<KnowledgeEvidence> fused = KnowledgeRetriever.reciprocalRankFusion(
                List.of(fullTextOnly, shared), List.of(vectorOnly, shared), 3);

        assertThat(fused).extracting(KnowledgeEvidence::chunkId)
                .containsExactly("C1", "C2", "C3");
        assertThat(fused.getFirst().score()).isGreaterThan(fused.get(1).score());
    }

    @Test
    void vectorEncodingRejectsDimensionDrift() {
        assertThatThrownBy(() -> KnowledgeVectorCodec.encode(new float[3], 1024))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected 1024 but got 3");
    }

    private KnowledgeEvidence evidence(String documentId, String chunkId) {
        return new KnowledgeEvidence(documentId, chunkId, "title", "section",
                "MICRO_BUSINESS", "2026.1", "content", 1.0);
    }
}
