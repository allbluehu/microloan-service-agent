package com.example.microloan.agent;

import com.example.microloan.application.ApplicationStatus;
import com.example.microloan.payment.PaymentHistory;
import com.example.microloan.repayment.NextRepaymentPlan;
import com.example.microloan.settlement.SettlementQuote;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {

    private final IntentClassifier intentClassifier;
    private final RepaymentTools repaymentTools;
    private final ApplicationTools applicationTools;
    private final PaymentTools paymentTools;
    private final SettlementTools settlementTools;

    public AgentOrchestrator(IntentClassifier intentClassifier, RepaymentTools repaymentTools,
                             ApplicationTools applicationTools, PaymentTools paymentTools,
                             SettlementTools settlementTools) {
        this.intentClassifier = intentClassifier;
        this.repaymentTools = repaymentTools;
        this.applicationTools = applicationTools;
        this.paymentTools = paymentTools;
        this.settlementTools = settlementTools;
    }

    public AgentState handle(String sessionId, String messageId, String content, String selectedLoanId,
                             String selectedApplicationId, LocalDate settlementDate) {
        AgentState state = new AgentState(UUID.randomUUID().toString(), sessionId, messageId, content,
                selectedLoanId, selectedApplicationId, settlementDate, Intent.UNKNOWN, null, null);
        state = state.withIntent(intentClassifier.classify(content));

        // The switch is the deterministic routing boundary; the model cannot select arbitrary application code.
        return switch (state.intent()) {
            case NEXT_REPAYMENT_QUERY -> queryNextRepayment(state);
            case APPLICATION_STATUS_QUERY -> queryApplicationStatus(state);
            case PAYMENT_RECORDS_QUERY -> queryPaymentRecords(state);
            case EARLY_SETTLEMENT_QUOTE -> calculateEarlySettlement(state);
            case UNKNOWN -> state.complete("当前支持查询下一期账单、贷款申请进度、还款记录和提前结清试算，请补充具体问题。");
        };
    }

    private AgentState queryNextRepayment(AgentState state) {
        if (isBlank(state.selectedLoanId())) {
            return state.complete("请先选择需要查询的贷款。");
        }
        NextRepaymentPlan plan = repaymentTools.getNextRepaymentPlan(state.selectedLoanId());
        // Financial values use a fixed template so the model cannot alter authoritative tool results.
        String answer = "贷款%s下一期应还%s元，还款日为%s（本金%s元、利息%s元、费用%s元）。"
                .formatted(plan.loanId(), plan.totalAmount().toPlainString(), plan.dueDate(),
                        plan.principal().toPlainString(), plan.interest().toPlainString(), plan.fee().toPlainString());
        return state.complete(new ToolResult.Repayment(plan), answer);
    }

    private AgentState queryApplicationStatus(AgentState state) {
        if (isBlank(state.selectedApplicationId())) {
            return state.complete("请先选择需要查询的贷款申请。");
        }
        ApplicationStatus status = applicationTools.getApplicationStatus(state.selectedApplicationId());
        String todo = status.missingDocuments().isEmpty()
                ? "当前没有待补资料。"
                : "待补资料：" + String.join("、", status.missingDocuments()) + "。";
        return state.complete(new ToolResult.Application(status), "申请%s当前状态为%s，更新时间为%s。%s"
                .formatted(status.applicationId(), status.status(), status.updatedAt(), todo));
    }

    private AgentState queryPaymentRecords(AgentState state) {
        if (isBlank(state.selectedLoanId())) {
            return state.complete("请先选择需要查询还款记录的贷款。");
        }
        PaymentHistory history = paymentTools.getPaymentRecords(state.selectedLoanId());
        String answer = history.records().isEmpty()
                ? "未查询到该贷款的还款记录。"
                : "已查询到贷款%s最近%s条还款记录，请查看业务卡片。"
                        .formatted(history.loanId(), history.records().size());
        return state.complete(new ToolResult.Payment(history), answer);
    }

    private AgentState calculateEarlySettlement(AgentState state) {
        if (isBlank(state.selectedLoanId())) {
            return state.complete("请先选择需要提前结清试算的贷款。");
        }
        if (state.settlementDate() == null) {
            return state.complete("请提供提前结清的试算日期。");
        }
        SettlementQuote quote = settlementTools.calculateEarlySettlement(state.selectedLoanId(), state.settlementDate());
        String answer = "贷款%s于%s提前结清的试算金额为%s元（剩余本金%s元、利息%s元、费用%s元）。%s"
                .formatted(quote.loanId(), state.settlementDate(), quote.totalAmount().toPlainString(),
                        quote.remainingPrincipal().toPlainString(), quote.interest().toPlainString(),
                        quote.fee().toPlainString(), quote.notice());
        return state.complete(new ToolResult.Settlement(quote), answer);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
