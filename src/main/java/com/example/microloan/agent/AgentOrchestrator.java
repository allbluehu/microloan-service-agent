package com.example.microloan.agent;

import com.example.microloan.application.ApplicationStatus;
import com.example.microloan.repayment.NextRepaymentPlan;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {

    private final IntentClassifier intentClassifier;
    private final RepaymentTools repaymentTools;
    private final ApplicationTools applicationTools;

    public AgentOrchestrator(IntentClassifier intentClassifier, RepaymentTools repaymentTools,
                             ApplicationTools applicationTools) {
        this.intentClassifier = intentClassifier;
        this.repaymentTools = repaymentTools;
        this.applicationTools = applicationTools;
    }

    public AgentState handle(String sessionId, String messageId, String content, String selectedLoanId,
                             String selectedApplicationId) {
        AgentState state = new AgentState(UUID.randomUUID().toString(), sessionId, messageId, content,
                selectedLoanId, selectedApplicationId, Intent.UNKNOWN, null, null);
        state = state.withIntent(intentClassifier.classify(content));

        // The switch is the deterministic routing boundary; the model cannot select arbitrary application code.
        return switch (state.intent()) {
            case NEXT_REPAYMENT_QUERY -> queryNextRepayment(state);
            case APPLICATION_STATUS_QUERY -> queryApplicationStatus(state);
            case UNKNOWN -> state.complete("当前支持查询下一期账单和贷款申请进度，请补充具体问题。");
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
