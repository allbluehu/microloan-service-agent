package com.example.microloan.agent;

/** 风险分类使用的受控类别，禁止模型返回任意文本类别。 */
public enum RiskCategory {
    /** 未发现风险。 */
    NONE,
    /** 疑似冒名申请或非本人贷款。 */
    IDENTITY_THEFT,
    /** 客户隐私或债务信息疑似泄露。 */
    PRIVACY_BREACH,
    /** 催收威胁、恐吓或辱骂。 */
    COLLECTION_ABUSE,
    /** 普通或监管投诉。 */
    COMPLAINT,
    /** 暴力、人身威胁或自伤信号。 */
    PERSONAL_SAFETY
}
