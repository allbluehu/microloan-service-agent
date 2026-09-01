package com.example.microloan.agent;

public record ChatSessionContext(
        /** 会话数据库主键。 */
        Long id,
        /** 最近一次明确选择的贷款编号。 */
        String selectedLoanId,
        /** 最近一次明确选择的贷款申请编号。 */
        String selectedApplicationId,
        /** 最近一次明确选择的产品编码。 */
        String selectedProductCode,
        /** 最近一次明确选择的地区编码。 */
        String selectedRegion
) {}
