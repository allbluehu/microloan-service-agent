package com.example.microloan.agent;

public record AgentState(
        String traceId,
        String sessionId,
        String messageId,
        String content,
        String selectedLoanId,
        String selectedApplicationId,
        Intent intent,
        ToolResult toolResult,
        String finalAnswer
) {
    public AgentState withIntent(Intent newIntent) {
        return new AgentState(traceId, sessionId, messageId, content, selectedLoanId, selectedApplicationId,
                newIntent, toolResult, finalAnswer);
    }

    public AgentState complete(ToolResult result, String answer) {
        return new AgentState(traceId, sessionId, messageId, content, selectedLoanId, selectedApplicationId,
                intent, result, answer);
    }

    public AgentState complete(String answer) {
        return complete(null, answer);
    }
}
