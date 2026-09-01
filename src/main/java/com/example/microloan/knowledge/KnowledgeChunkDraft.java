package com.example.microloan.knowledge;

public record KnowledgeChunkDraft(
        /** 文档内稳定且可重复生成的切片序号。 */
        String chunkNo,
        /** 切片所在的 Markdown 标题层级路径。 */
        String sectionPath,
        /** 保持原段落语义的切片正文。 */
        String content,
        /** 包含元数据关键词的全文检索文本。 */
        String searchText,
        /** 切片正文的 SHA-256 校验和。 */
        String checksum
) {}
