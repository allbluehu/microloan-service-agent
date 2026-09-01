package com.example.microloan.knowledge;

public record KnowledgeChunkForEmbedding(
        /** 知识切片数据库主键。 */
        long id,
        /** 需要生成向量的切片正文。 */
        String content
) {}
