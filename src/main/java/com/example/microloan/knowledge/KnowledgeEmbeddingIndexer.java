package com.example.microloan.knowledge;

import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnBean(EmbeddingModel.class)
@ConditionalOnProperty(name = "knowledge.vector.indexing-enabled", havingValue = "true")
public class KnowledgeEmbeddingIndexer implements ApplicationRunner {

    private static final int EMBEDDING_DIMENSIONS = 1024;

    private final KnowledgeMapper knowledgeMapper;
    private final EmbeddingModel embeddingModel;
    private final String modelName;

    public KnowledgeEmbeddingIndexer(KnowledgeMapper knowledgeMapper,
                                     EmbeddingModel embeddingModel,
                                     @Value("${knowledge.vector.model:embedding-3}") String modelName) {
        this.knowledgeMapper = knowledgeMapper;
        this.embeddingModel = embeddingModel;
        this.modelName = modelName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<KnowledgeChunkForEmbedding> chunks = knowledgeMapper.findChunksRequiringEmbedding(modelName);
        for (KnowledgeChunkForEmbedding chunk : chunks) {
            // 向量同时记录模型名，切换模型后可自动识别并受控重建旧索引。
            String vector = KnowledgeVectorCodec.encode(
                    embeddingModel.embed(chunk.content()), EMBEDDING_DIMENSIONS);
            knowledgeMapper.updateEmbedding(chunk.id(), vector, modelName);
        }
    }
}
