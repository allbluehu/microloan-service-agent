package com.example.microloan.knowledge;

import java.time.OffsetDateTime;

public record KnowledgeDocumentIdentity(
        /** 知识文档数据库主键。 */
        long id,
        /** 知识文档的稳定业务编号。 */
        String documentNo,
        /** 知识文档版本号。 */
        String version,
        /** 原始文档内容校验和。 */
        String checksum,
        /** 文档当前发布状态。 */
        String status,
        /** 最近一次完成审核的审核人。 */
        String reviewer,
        /** 文档通过审核的时间。 */
        OffsetDateTime approvedAt,
        /** 文档正式发布的时间。 */
        OffsetDateTime publishedAt,
        /** 文档下线失效的时间。 */
        OffsetDateTime expiredAt
) {}
