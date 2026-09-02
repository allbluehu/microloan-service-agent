package com.example.microloan.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/**
 * 知识向量补建服务的断点续建与模型版本测试。
 * 调用链为“管理 API/启动 Runner → KnowledgeEmbeddingService → 查询缺失或旧模型切片 → EmbeddingModel
 * → 1024 维编码 → KnowledgeMapper 更新模型版本”。Mapper 和模型均使用 Mock，普通测试不会访问 GLM。
 */
class KnowledgeEmbeddingServiceTest {

    @Test
    void indexesOnlyChunksSelectedForCurrentModel() {
        KnowledgeMapper mapper = mock(KnowledgeMapper.class);
        EmbeddingModel model = mock(EmbeddingModel.class);
        float[] vector = new float[1024];
        vector[0] = 1.0f;
        when(mapper.findChunksRequiringEmbedding("embedding-3"))
                .thenReturn(List.of(new KnowledgeChunkForEmbedding(101L, "待向量化内容")));
        when(model.embed("待向量化内容")).thenReturn(vector);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("embeddingModel", model);
        KnowledgeEmbeddingService service = new KnowledgeEmbeddingService(
                mapper, beans.getBeanProvider(EmbeddingModel.class), "embedding-3");

        KnowledgeEmbeddingIndexResult result = service.indexPendingChunks();

        assertThat(result.indexedChunkCount()).isEqualTo(1);
        assertThat(result.model()).isEqualTo("embedding-3");
        assertThat(result.dimensions()).isEqualTo(1024);
        verify(mapper).updateEmbedding(101L, KnowledgeVectorCodec.encode(vector, 1024), "embedding-3");
    }
}
