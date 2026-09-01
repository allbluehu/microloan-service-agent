package com.example.microloan.knowledge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeRetriever {

    private static final int EMBEDDING_DIMENSIONS = 1024;
    private static final int CANDIDATE_LIMIT = 20;
    private static final int RESULT_LIMIT = 3;
    private static final double RRF_K = 60.0;

    private final KnowledgeMapper knowledgeMapper;
    private final Optional<EmbeddingModel> embeddingModel;
    private final boolean vectorEnabled;
    private final KnowledgeReranker reranker;

    public KnowledgeRetriever(KnowledgeMapper knowledgeMapper,
                              ObjectProvider<EmbeddingModel> embeddingModelProvider,
                              @Value("${knowledge.vector.enabled:false}") boolean vectorEnabled,
                              KnowledgeReranker reranker) {
        this.knowledgeMapper = knowledgeMapper;
        this.embeddingModel = Optional.ofNullable(embeddingModelProvider.getIfAvailable());
        this.vectorEnabled = vectorEnabled;
        this.reranker = reranker;
    }

    public List<KnowledgeEvidence> retrieve(KnowledgeQuery query) {
        List<KnowledgeEvidence> fullText = knowledgeMapper.searchPublished(
                query.searchText(), query.productCode(), query.region(), CANDIDATE_LIMIT);
        if (!vectorEnabled || embeddingModel.isEmpty()) {
            // 向量能力是可选增强；缺少模型或未启用时保留可审计的全文检索路径。
            return reranker.rerank(query.searchText(), fullText, RESULT_LIMIT);
        }

        String queryVector = KnowledgeVectorCodec.encode(
                embeddingModel.orElseThrow().embed(query.searchText()), EMBEDDING_DIMENSIONS);
        List<KnowledgeEvidence> vector = knowledgeMapper.searchPublishedByVector(
                queryVector, query.productCode(), query.region(), CANDIDATE_LIMIT);
        List<KnowledgeEvidence> fused = reciprocalRankFusion(fullText, vector, CANDIDATE_LIMIT);
        return reranker.rerank(query.searchText(), fused, RESULT_LIMIT);
    }

    static List<KnowledgeEvidence> reciprocalRankFusion(List<KnowledgeEvidence> fullText,
                                                         List<KnowledgeEvidence> vector,
                                                         int limit) {
        // RRF 只使用名次而非不可直接比较的 FTS 与余弦原始分数。
        Map<String, RankedEvidence> fused = new LinkedHashMap<>();
        addRanking(fused, fullText);
        addRanking(fused, vector);
        return fused.values().stream()
                .sorted((left, right) -> Double.compare(right.score, left.score))
                .limit(limit)
                .map(ranked -> withScore(ranked.evidence, ranked.score))
                .toList();
    }

    private static void addRanking(Map<String, RankedEvidence> fused, List<KnowledgeEvidence> ranking) {
        for (int index = 0; index < ranking.size(); index++) {
            KnowledgeEvidence evidence = ranking.get(index);
            String key = evidence.documentId() + ":" + evidence.chunkId();
            double contribution = 1.0 / (RRF_K + index + 1);
            fused.compute(key, (ignored, current) -> current == null
                    ? new RankedEvidence(evidence, contribution)
                    : new RankedEvidence(current.evidence, current.score + contribution));
        }
    }

    private static KnowledgeEvidence withScore(KnowledgeEvidence evidence, double score) {
        return new KnowledgeEvidence(evidence.documentId(), evidence.chunkId(), evidence.title(),
                evidence.sectionPath(), evidence.productCode(), evidence.version(), evidence.content(), score);
    }

    private record RankedEvidence(
            /** 去重后保留的完整知识证据。 */
            KnowledgeEvidence evidence,
            /** 多路排名累加得到的 RRF 分数。 */
            double score
    ) {}
}
