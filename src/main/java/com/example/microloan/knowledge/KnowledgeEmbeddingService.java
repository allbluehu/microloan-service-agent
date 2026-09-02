package com.example.microloan.knowledge;

import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeEmbeddingService {

    private static final int EMBEDDING_DIMENSIONS = 1024;

    private final KnowledgeMapper knowledgeMapper;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final String modelName;

    public KnowledgeEmbeddingService(KnowledgeMapper knowledgeMapper,
                                     ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                     @Value("${knowledge.vector.model:embedding-3}") String modelName) {
        this.knowledgeMapper = knowledgeMapper;
        this.embeddingModelProvider = embeddingModelProvider;
        this.modelName = modelName;
    }

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
