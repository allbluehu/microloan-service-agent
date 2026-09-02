package com.example.microloan.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * KnowledgeRetriever 的纯算法边界测试。
 * 生产调用链为“全文召回 + 查询向量召回 → RRF 去重融合 → 词项重排”；本类隔离数据库和模型，
 * 验证两路共同命中的证据获得累积分数，以及向量维度漂移在进入 pgvector 之前被拒绝。
 */
class KnowledgeRetrieverTest {

    @Test
    void reciprocalRankFusionRewardsEvidenceReturnedByBothRetrievers() {
        // shared 在两路都排第二，但两次 RRF 贡献之和应超过任一单路第一名。
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
        // DDL 固定为 VECTOR(1024)，提前失败能给出明确模型配置错误而不是模糊数据库异常。
        assertThatThrownBy(() -> KnowledgeVectorCodec.encode(new float[3], 1024))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected 1024 but got 3");
    }

    private KnowledgeEvidence evidence(String documentId, String chunkId) {
        return new KnowledgeEvidence(documentId, chunkId, "title", "section",
                "MICRO_BUSINESS", "2026.1", "content", 1.0);
    }
}
