package com.example.microloan.knowledge;

public record KnowledgeQuery(
        /** 归一化后提交给检索器的查询文本。 */
        String searchText,
        /** 用于强过滤的产品编码，可为空。 */
        String productCode,
        /** 用于知识适用范围过滤的地区编码。 */
        String region
) {}
