package com.example.microloan.knowledge;

public record KnowledgeEmbeddingIndexResult(
        /** 本次建立或重建向量的切片数量。 */
        int indexedChunkCount,
        /** 本次索引使用的嵌入模型版本。 */
        String model,
        /** 数据库向量列要求的固定维度。 */
        int dimensions
) {}
