package com.example.microloan.application;

import java.time.OffsetDateTime;

public record ApplicationRow(
        /** 贷款申请对外编号。 */
        String applicationNo,
        /** 申请所属客户主键。 */
        Long customerId,
        /** 申请对应的产品编码。 */
        String productCode,
        /** 数据库存储的申请状态。 */
        String status,
        /** 申请状态原因码。 */
        String reasonCode,
        /** 数据库存储的逗号分隔缺失材料。 */
        String missingDocuments,
        /** 申请状态最后更新时间。 */
        OffsetDateTime updatedAt
) {}
