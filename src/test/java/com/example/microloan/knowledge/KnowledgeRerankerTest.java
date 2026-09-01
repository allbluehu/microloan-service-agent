package com.example.microloan.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeRerankerTest {
    private final KnowledgeReranker reranker = new KnowledgeReranker();

    @Test
    void rewardsCandidatesCoveringMoreQueryTerms() {
        KnowledgeEvidence partial = evidence("C1", "申请材料说明", "需要营业执照", 0.9);
        KnowledgeEvidence complete = evidence("C2", "经营贷申请材料", "需要营业执照和身份证件", 0.2);

        List<KnowledgeEvidence> result = reranker.rerank("经营贷 材料 身份证件",
                List.of(partial, complete), 2);

        assertThat(result).extracting(KnowledgeEvidence::chunkId).containsExactly("C2", "C1");
    }

    @Test
    void preservesRetrievalScoreWhenCoverageIsEqual() {
        KnowledgeEvidence lower = evidence("C1", "材料", "申请说明", 0.2);
        KnowledgeEvidence higher = evidence("C2", "材料", "申请说明", 0.8);

        assertThat(reranker.rerank("材料", List.of(lower, higher), 2))
                .extracting(KnowledgeEvidence::chunkId)
                .containsExactly("C2", "C1");
    }

    private KnowledgeEvidence evidence(String chunkId, String title, String content, double score) {
        return new KnowledgeEvidence("D1", chunkId, title, "申请指南", "MICRO_BUSINESS",
                "2026.1", content, score);
    }
}
