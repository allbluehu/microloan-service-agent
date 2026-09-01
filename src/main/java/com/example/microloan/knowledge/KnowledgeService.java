package com.example.microloan.knowledge;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {

    private final KnowledgeRetriever knowledgeRetriever;
    private final KnowledgeQueryNormalizer queryNormalizer;

    public KnowledgeService(KnowledgeRetriever knowledgeRetriever, KnowledgeQueryNormalizer queryNormalizer) {
        this.knowledgeRetriever = knowledgeRetriever;
        this.queryNormalizer = queryNormalizer;
    }

    @Transactional(readOnly = true)
    public KnowledgeAnswer answer(String message, String selectedProductCode, String selectedRegion) {
        KnowledgeQuery query = queryNormalizer.normalize(message, selectedProductCode, selectedRegion);
        List<KnowledgeEvidence> evidence = List.copyOf(knowledgeRetriever.retrieve(query));
        if (evidence.isEmpty()) {
            return new KnowledgeAnswer("未找到当前有效且可对客展示的知识，请补充产品名称或转人工客服。", List.of());
        }
        // Until a generation model is configured, evidence is returned verbatim to avoid unsupported synthesis.
        String answer = evidence.stream().map(KnowledgeEvidence::content).distinct()
                .reduce((left, right) -> left + "\n" + right).orElseThrow();
        return new KnowledgeAnswer(answer, evidence);
    }
}
