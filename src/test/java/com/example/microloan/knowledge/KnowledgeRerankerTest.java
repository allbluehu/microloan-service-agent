package com.example.microloan.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 混合召回后的确定性重排测试。
 * RRF/全文/向量先产生候选及原始分数，KnowledgeReranker 再按查询词覆盖度排序；覆盖度相同时保留上游相关性，
 * 不引入另一个不可解释模型。
 */
class KnowledgeRerankerTest {
    private final KnowledgeReranker reranker = new KnowledgeReranker();

    @Test
    void rewardsCandidatesCoveringMoreQueryTerms() {
        // 即使上游分数较低，覆盖更多明确查询词的证据也应优先，降低泛化向量结果抢占首位的概率。
        KnowledgeEvidence partial = evidence("C1", "申请材料说明", "需要营业执照", 0.9);
        KnowledgeEvidence complete = evidence("C2", "经营贷申请材料", "需要营业执照和身份证件", 0.2);

        List<KnowledgeEvidence> result = reranker.rerank("经营贷 材料 身份证件",
                List.of(partial, complete), 2);

        assertThat(result).extracting(KnowledgeEvidence::chunkId).containsExactly("C2", "C1");
    }

    @Test
    void preservesRetrievalScoreWhenCoverageIsEqual() {
        // 覆盖度打平时不能任意重排，保留上游分数确保结果稳定且可解释。
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
