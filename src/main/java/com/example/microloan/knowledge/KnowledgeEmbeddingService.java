package com.example.microloan.knowledge;

import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
/**
 * 知识切片向量索引服务，供管理 API 和可选启动索引器共同调用。
 *
 * <p>待处理集合由“向量为空或 embedding_model 与当前模型名不一致”确定。每条切片生成后立即独立更新，
 * 方法不包裹长事务，因此供应商限流或进程中断不会回滚已完成工作，下次调用会从剩余切片继续。</p>
 */
public class KnowledgeEmbeddingService {

    private static final int EMBEDDING_DIMENSIONS = 1024;

    private final KnowledgeMapper knowledgeMapper;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final String modelName;

    public KnowledgeEmbeddingService(KnowledgeMapper knowledgeMapper,
                                     ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                     @Value("${knowledge.vector.model:text-embedding-v4}") String modelName) {
        this.knowledgeMapper = knowledgeMapper;
        this.embeddingModelProvider = embeddingModelProvider;
        this.modelName = modelName;
    }

    /**
     * 为全部待处理切片补建 1024 维向量。
     *
     * @return 本次选中的切片数、模型版本和固定维度
     * @throws EmbeddingModelUnavailableException 应用未配置 EmbeddingModel 时抛出，管理 API 映射为 503
     */
    public KnowledgeEmbeddingIndexResult indexPendingChunks() {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            throw new EmbeddingModelUnavailableException();
        }
        List<KnowledgeChunkForEmbedding> chunks = knowledgeMapper.findChunksRequiringEmbedding(modelName);
        for (KnowledgeChunkForEmbedding chunk : chunks) {
            // 每个切片独立写入且查询只选择缺失/旧模型向量，中断后可安全重试并从断点继续。
            String vector = KnowledgeVectorCodec.encode(
                    embeddingModel.embed(chunk.content()), EMBEDDING_DIMENSIONS);
            knowledgeMapper.updateEmbedding(chunk.id(), vector, modelName);
        }
        return new KnowledgeEmbeddingIndexResult(chunks.size(), modelName, EMBEDDING_DIMENSIONS);
    }
}
