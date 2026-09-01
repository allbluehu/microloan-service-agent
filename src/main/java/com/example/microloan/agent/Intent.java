package com.example.microloan.agent;

public enum Intent {
    /** 查询下一期应还账单。 */
    NEXT_REPAYMENT_QUERY,
    /** 查询贷款申请进度。 */
    APPLICATION_STATUS_QUERY,
    /** 查询历史还款记录。 */
    PAYMENT_RECORDS_QUERY,
    /** 计算提前结清试算。 */
    EARLY_SETTLEMENT_QUOTE,
    /** 涉及隐私、冒名或催收等高风险投诉。 */
    HIGH_RISK_COMPLAINT,
    /** 用户明确要求转接人工。 */
    HUMAN_HANDOFF,
    /** 查询公开产品或流程知识。 */
    KNOWLEDGE_QUERY,
    /** 同一轮包含多个业务动作，需要用户明确优先目标。 */
    CLARIFICATION_REQUIRED,
    /** 无法可靠归类的意图。 */
    UNKNOWN
}
