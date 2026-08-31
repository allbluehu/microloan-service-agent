package com.example.microloan.agent;

import java.time.LocalDate;

public record AgentState(
        String traceId,
        String sessionId,
        String messageId,
        String content,
        String selectedLoanId,
        String selectedApplicationId,
        LocalDate settlementDate,
        Intent intent,
        ToolResult toolResult,
        String finalAnswer
) {
    public AgentState withIntent(Intent newIntent) {
        return new AgentState(traceId, sessionId, messageId, content, selectedLoanId, selectedApplicationId,
                settlementDate, newIntent, toolResult, finalAnswer);
    }

    public AgentState complete(ToolResult result, String answer) {
        return new AgentState(traceId, sessionId, messageId, content, selectedLoanId, selectedApplicationId,
                settlementDate, intent, result, answer);
    }

    public AgentState complete(String answer) {
        return complete(null, answer);
    }
}
