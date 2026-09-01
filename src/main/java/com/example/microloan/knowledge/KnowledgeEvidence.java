package com.example.microloan.knowledge;

public record KnowledgeEvidence(
        /** 来源知识文档的稳定编号。 */
        String documentId,
        /** 来源知识切片的稳定编号。 */
        String chunkId,
        /** 来源文档标题。 */
        String title,
        /** 切片在文档中的章节路径。 */
        String sectionPath,
        /** 来源知识适用的产品编码。 */
        String productCode,
        /** 来源知识文档版本。 */
        String version,
        /** 可用于回答的切片正文。 */
        String content,
        /** 当前检索或融合阶段计算的相关性分数。 */
        double score
) {}
