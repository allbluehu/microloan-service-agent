package com.example.microloan.agent;

import com.example.microloan.ticket.TicketType;

public record RiskAssessment(
        /** 当前消息的风险等级。 */
        RiskLevel level,
        /** 高风险时建议创建的工单类型，低风险时为空。 */
        TicketType ticketType,
        /** 风险所属的受控业务类别。 */
        RiskCategory category,
        /** 风险判断来自规则、模型还是安全降级。 */
        RiskSource source,
        /** 判断置信度；规则明确命中为 1，模型结果使用协议返回值。 */
        double confidence,
        /** 命中的稳定规则编号；非规则结果为空。 */
        String ruleCode
) {

    public RiskAssessment(RiskLevel level, TicketType ticketType) {
        this(level, ticketType, categoryOf(ticketType), RiskSource.RULE, 1.0, null);
    }

    public static RiskAssessment low() {
        return new RiskAssessment(RiskLevel.LOW, null, RiskCategory.NONE, RiskSource.DEFAULT, 1.0, null);
    }

    private static RiskCategory categoryOf(TicketType type) {
        if (type == null) {
            return RiskCategory.NONE;
        }
        return switch (type) {
            case IDENTITY_THEFT -> RiskCategory.IDENTITY_THEFT;
            case PRIVACY_BREACH -> RiskCategory.PRIVACY_BREACH;
            case COLLECTION_COMPLAINT -> RiskCategory.COLLECTION_ABUSE;
            default -> RiskCategory.COMPLAINT;
        };
    }
}
