package com.example.microloan.knowledge;

public record KnowledgeImportResult(
        /** 导入或已存在的知识文档数据库主键。 */
        long documentId,
        /** 知识文档的稳定业务编号。 */
        String documentNo,
        /** 知识文档版本号。 */
        String version,
        /** 当前版本包含的切片数量。 */
        int chunkCount,
        /** 是否命中相同内容的既有版本。 */
        boolean duplicate
) {}
