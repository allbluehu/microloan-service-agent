package com.example.microloan.application;

import java.time.OffsetDateTime;
import java.util.List;

public record ApplicationStatus(
        /** 贷款申请对外编号。 */
        String applicationId,
        /** 申请对应的产品编码。 */
        String productCode,
        /** 当前申请状态。 */
        String status,
        /** 当前状态的原因码。 */
        String reasonCode,
        /** 仍需客户补充的材料列表。 */
        List<String> missingDocuments,
        /** 申请状态最后更新时间。 */
        OffsetDateTime updatedAt
) {}
