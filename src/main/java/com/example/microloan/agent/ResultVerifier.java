package com.example.microloan.agent;

import com.example.microloan.knowledge.KnowledgeAnswer;
import com.example.microloan.payment.PaymentHistory;
import com.example.microloan.repayment.NextRepaymentPlan;
import com.example.microloan.settlement.SettlementQuote;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** 在输出渲染后验证意图、资源标识和权威业务结果之间的一致性。 */
@Component
public class ResultVerifier {

    /** 校验失败直接阻断响应，避免错误金融数据通过模板和输出护栏继续返回客户。 */
    public AgentState verify(AgentState state) {
        switch (state.intent()) {
            case NEXT_REPAYMENT_QUERY -> verifyRepayment(state);
            case APPLICATION_STATUS_QUERY -> verifyApplication(state);
            case PAYMENT_RECORDS_QUERY -> verifyPayment(state);
            case EARLY_SETTLEMENT_QUOTE -> verifySettlement(state);
            case HIGH_RISK_COMPLAINT, HUMAN_HANDOFF -> requireType(state, ToolResult.Ticket.class);
            case KNOWLEDGE_QUERY -> verifyKnowledge(state);
            case CLARIFICATION_REQUIRED, UNKNOWN -> requireNoResult(state);
        }
        if (state.finalAnswer() == null || state.finalAnswer().isBlank()) {
            throw new IllegalStateException("Agent final answer must not be blank");
        }
        return state;
    }

    private void verifyRepayment(AgentState state) {
        if (state.toolResult() == null && isBlank(state.selectedLoanId())) {
            return; // 缺少贷款编号时路由只生成澄清文案，没有执行领域查询。
        }
        ToolResult.Repayment result = requireType(state, ToolResult.Repayment.class);
        NextRepaymentPlan plan = result.value();
        requireSameResource(state.selectedLoanId(), plan.loanId());
        requireNonNegative(plan.principal(), plan.interest(), plan.fee(), plan.totalAmount());
        requireEqual(plan.totalAmount(), plan.principal().add(plan.interest()).add(plan.fee()));
        if (plan.dueDate() == null || plan.status() == null) {
            throw new IllegalStateException("Repayment plan date and status are required");
        }
    }

    private void verifyApplication(AgentState state) {
        if (state.toolResult() == null && isBlank(state.selectedApplicationId())) {
            return;
        }
        ToolResult.Application result = requireType(state, ToolResult.Application.class);
        requireSameResource(state.selectedApplicationId(), result.value().applicationId());
        if (result.value().status() == null || result.value().updatedAt() == null) {
            throw new IllegalStateException("Application status and update time are required");
        }
    }

    private void verifyPayment(AgentState state) {
        if (state.toolResult() == null && isBlank(state.selectedLoanId())) {
            return;
        }
        ToolResult.Payment result = requireType(state, ToolResult.Payment.class);
        PaymentHistory history = result.value();
        requireSameResource(state.selectedLoanId(), history.loanId());
        if (history.records() == null) {
            throw new IllegalStateException("Payment records are required");
        }
    }

    private void verifySettlement(AgentState state) {
        if (state.toolResult() == null && (isBlank(state.selectedLoanId()) || state.settlementDate() == null)) {
            return;
        }
        ToolResult.Settlement result = requireType(state, ToolResult.Settlement.class);
        SettlementQuote quote = result.value();
        requireSameResource(state.selectedLoanId(), quote.loanId());
        requireNonNegative(quote.remainingPrincipal(), quote.interest(), quote.fee(), quote.totalAmount());
        requireEqual(quote.totalAmount(), quote.remainingPrincipal().add(quote.interest()).add(quote.fee()));
        if (quote.validUntil() == null) {
            throw new IllegalStateException("Settlement quote expiry is required");
        }
    }

    private void verifyKnowledge(AgentState state) {
        if (state.toolResult() == null) {
            return; // 无证据时 KnowledgeService 使用稳定拒答且不构造业务卡片。
        }
        ToolResult.Knowledge result = requireType(state, ToolResult.Knowledge.class);
        KnowledgeAnswer answer = result.value();
        if (answer.sources() == null || answer.sources().isEmpty()) {
            throw new IllegalStateException("Knowledge business card requires evidence sources");
        }
    }

    private void requireNoResult(AgentState state) {
        if (state.toolResult() != null) {
            throw new IllegalStateException("Non-business intent must not return a business result");
        }
    }

    private void requireSameResource(String selected, String actual) {
        if (selected == null || !selected.equals(actual)) {
            throw new IllegalStateException("Business result resource does not match selected resource");
        }
    }

    private void requireNonNegative(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value == null || value.signum() < 0) {
                throw new IllegalStateException("Financial amount must be present and non-negative");
            }
        }
    }

    private void requireEqual(BigDecimal actual, BigDecimal expected) {
        if (actual.compareTo(expected) != 0) {
            throw new IllegalStateException("Financial amount components do not equal total amount");
        }
    }

    private <T extends ToolResult> T requireType(AgentState state, Class<T> expectedType) {
        if (!expectedType.isInstance(state.toolResult())) {
            throw new IllegalStateException("Tool result type does not match intent " + state.intent());
        }
        return expectedType.cast(state.toolResult());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
