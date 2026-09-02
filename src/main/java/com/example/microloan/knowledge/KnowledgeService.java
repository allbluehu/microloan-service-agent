package com.example.microloan.knowledge;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * 面向 Agent 的知识问答门面，隔离查询规范化、召回实现和最终回答策略。
 *
 * <p>当前不让生成模型自由改写证据：召回为空时稳定拒答，召回成功时按排序返回去重后的证据正文和引用，
 * 从而保证回答内容可追溯且不会超出知识库证据。</p>
 */
public class KnowledgeService {

    private final KnowledgeRetriever knowledgeRetriever;
    private final KnowledgeQueryNormalizer queryNormalizer;

    public KnowledgeService(KnowledgeRetriever knowledgeRetriever, KnowledgeQueryNormalizer queryNormalizer) {
        this.knowledgeRetriever = knowledgeRetriever;
        this.queryNormalizer = queryNormalizer;
    }

    @Transactional(readOnly = true)
    /**
     * 根据客户问题及会话槽位检索当前有效的对客知识。
     *
     * @return 有证据时包含正文与引用；无证据时包含拒答文案和空引用列表
     */
    public KnowledgeAnswer answer(String message, String selectedProductCode, String selectedRegion) {
        KnowledgeQuery query = queryNormalizer.normalize(message, selectedProductCode, selectedRegion);
        List<KnowledgeEvidence> evidence = List.copyOf(knowledgeRetriever.retrieve(query));
        if (evidence.isEmpty()) {
            return new KnowledgeAnswer("未找到当前有效且可对客展示的知识，请补充产品名称或转人工客服。", List.of());
        }
        // 当前直接拼接去重证据，避免生成模型在金融知识范围外补充未经发布的结论。
        String answer = evidence.stream().map(KnowledgeEvidence::content).distinct()
                .reduce((left, right) -> left + "\n" + right).orElseThrow();
        return new KnowledgeAnswer(answer, evidence);
    }
}
