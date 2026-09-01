package com.example.microloan.ticket;

public enum TicketType {
    /** 催收行为相关投诉。 */
    COLLECTION_COMPLAINT,
    /** 疑似冒名贷款或身份盗用。 */
    IDENTITY_THEFT,
    /** 客户隐私疑似泄露。 */
    PRIVACY_BREACH,
    /** 已还款但账单尚未入账。 */
    PAYMENT_NOT_POSTED,
    /** 不属于特定高风险类型的一般投诉。 */
    GENERAL_COMPLAINT
}
