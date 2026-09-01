package com.example.microloan.agent;

import java.time.LocalDate;
import com.example.microloan.ticket.TicketType;

public record AgentState(
        /** 本轮 Agent 执行的全链路追踪标识。 */
        String traceId,
        /** 承载多轮对话上下文的会话标识。 */
        String sessionId,
        /** 客户端提供的消息幂等与审计标识。 */
        String messageId,
        /** 当前用户消息原文，仅在本轮内处理。 */
        String content,
        /** 用户明确选择的贷款编号。 */
        String selectedLoanId,
        /** 用户明确选择的申请编号。 */
        String selectedApplicationId,
        /** 用户明确选择或系统推断的产品编码。 */
        String selectedProductCode,
        /** 知识检索使用的地区编码。 */
        String selectedRegion,
        /** 提前结清试算日期。 */
        LocalDate settlementDate,
        /** 风险分类节点给出的风险等级。 */
        RiskLevel riskLevel,
        /** 高风险场景对应的工单类型。 */
        TicketType ticketType,
        /** 意图分类节点给出的当前意图。 */
        Intent intent,
        /** 受控工具或知识检索返回的结构化结果。 */
        ToolResult toolResult,
        /** 最终返回给客户的文本答案。 */
        String finalAnswer
) {
    public AgentState withIntent(Intent newIntent) {
        return new AgentState(traceId, sessionId, messageId, content, selectedLoanId, selectedApplicationId,
                selectedProductCode, selectedRegion, settlementDate, riskLevel, ticketType, newIntent,
                toolResult, finalAnswer);
    }

    public AgentState withRisk(RiskAssessment assessment) {
        return new AgentState(traceId, sessionId, messageId, content, selectedLoanId, selectedApplicationId,
                selectedProductCode, selectedRegion, settlementDate, assessment.level(), assessment.ticketType(),
                intent, toolResult, finalAnswer);
    }

    public AgentState complete(ToolResult result, String answer) {
        return new AgentState(traceId, sessionId, messageId, content, selectedLoanId, selectedApplicationId,
                selectedProductCode, selectedRegion, settlementDate, riskLevel, ticketType, intent, result, answer);
    }

    public AgentState complete(String answer) {
        return complete(null, answer);
    }
}
