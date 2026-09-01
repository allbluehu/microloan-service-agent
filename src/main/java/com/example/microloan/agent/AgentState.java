package com.example.microloan.agent;

import java.time.LocalDate;
import com.example.microloan.ticket.TicketType;

public record AgentState(
        String traceId,
        String sessionId,
        String messageId,
        String content,
        String selectedLoanId,
        String selectedApplicationId,
        LocalDate settlementDate,
        RiskLevel riskLevel,
        TicketType ticketType,
        Intent intent,
        ToolResult toolResult,
        String finalAnswer
) {
    public AgentState withIntent(Intent newIntent) {
        return new AgentState(traceId, sessionId, messageId, content, selectedLoanId, selectedApplicationId,
                settlementDate, riskLevel, ticketType, newIntent, toolResult, finalAnswer);
    }

    public AgentState withRisk(RiskAssessment assessment) {
        return new AgentState(traceId, sessionId, messageId, content, selectedLoanId, selectedApplicationId,
                settlementDate, assessment.level(), assessment.ticketType(), intent, toolResult, finalAnswer);
    }

    public AgentState complete(ToolResult result, String answer) {
        return new AgentState(traceId, sessionId, messageId, content, selectedLoanId, selectedApplicationId,
                settlementDate, riskLevel, ticketType, intent, result, answer);
    }

    public AgentState complete(String answer) {
        return complete(null, answer);
    }
}
