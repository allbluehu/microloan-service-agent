package com.example.microloan.knowledge;

import java.time.LocalDate;
import java.util.List;

public record KnowledgeMarkdownDocument(
        /** 知识文档的稳定业务编号。 */
        String documentNo,
        /** 面向审核人与客户展示的文档标题。 */
        String title,
        /** 文档适用的产品编码。 */
        String productCode,
        /** 文档适用的地区编码，ALL 表示全地区。 */
        String region,
        /** 文档版本号。 */
        String version,
        /** 文档访问范围，如 PUBLIC、CUSTOMER 或 INTERNAL。 */
        String audience,
        /** 文档开始生效的日期。 */
        LocalDate effectiveFrom,
        /** 文档失效日期，长期有效时为空。 */
        LocalDate effectiveTo,
        /** 文档审核人。 */
        String reviewer,
        /** 提供给全文检索的受控业务关键词。 */
        List<String> keywords,
        /** 去除元数据头后的 Markdown 正文。 */
        String body,
        /** 原始 Markdown 内容的 SHA-256 校验和。 */
        String checksum
) {}
