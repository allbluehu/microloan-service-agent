package com.example.microloan.agent;

import com.example.microloan.ticket.TicketType;

public record RiskAssessment(
        /** 当前消息的风险等级。 */
        RiskLevel level,
        /** 高风险时建议创建的工单类型，低风险时为空。 */
        TicketType ticketType
) {

    public static RiskAssessment low() {
        return new RiskAssessment(RiskLevel.LOW, null);
    }
}
