package com.example.microloan.knowledge;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(EmbeddingModel.class)
@ConditionalOnProperty(name = "knowledge.vector.indexing-enabled", havingValue = "true")
public class KnowledgeEmbeddingIndexer implements ApplicationRunner {

    private final KnowledgeEmbeddingService embeddingService;

    public KnowledgeEmbeddingIndexer(KnowledgeEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 启动开关与管理 API 复用同一索引服务，避免两条路径产生不同的版本或断点续建语义。
        embeddingService.indexPendingChunks();
    }
}
