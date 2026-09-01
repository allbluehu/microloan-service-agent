package com.example.microloan.agent;

import com.example.microloan.application.ApplicationStatus;
import com.example.microloan.knowledge.KnowledgeAnswer;
import com.example.microloan.payment.PaymentHistory;
import com.example.microloan.repayment.NextRepaymentPlan;
import com.example.microloan.settlement.SettlementQuote;
import com.example.microloan.ticket.ServiceTicket;
import com.fasterxml.jackson.annotation.JsonValue;

public sealed interface ToolResult permits ToolResult.Application, ToolResult.Payment, ToolResult.Repayment,
        ToolResult.Settlement, ToolResult.Ticket, ToolResult.Knowledge {

    record Application(
            /** 申请进度工具返回的业务数据。 */
            @JsonValue ApplicationStatus value
    ) implements ToolResult {}

    record Payment(
            /** 还款记录工具返回的业务数据。 */
            @JsonValue PaymentHistory value
    ) implements ToolResult {}

    record Repayment(
            /** 下一期账单工具返回的业务数据。 */
            @JsonValue NextRepaymentPlan value
    ) implements ToolResult {}

    record Settlement(
            /** 提前结清工具返回的试算数据。 */
            @JsonValue SettlementQuote value
    ) implements ToolResult {}

    record Ticket(
            /** 工单工具返回的工单数据。 */
            @JsonValue ServiceTicket value
    ) implements ToolResult {}

    record Knowledge(
            /** 知识检索返回的答案与引用。 */
            @JsonValue KnowledgeAnswer value
    ) implements ToolResult {}
}
