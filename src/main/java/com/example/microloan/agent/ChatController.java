package com.example.microloan.agent;

import com.example.microloan.repayment.NextRepaymentPlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatController {

    private final IntentClassifier intentClassifier;
    private final RepaymentTools repaymentTools;

    public ChatController(IntentClassifier intentClassifier, RepaymentTools repaymentTools) {
        this.intentClassifier = intentClassifier;
        this.repaymentTools = repaymentTools;
    }

    @PostMapping("/{sessionId}/messages")
    public ChatResponse send(@PathVariable String sessionId, @Valid @RequestBody ChatRequest request) {
        String traceId = UUID.randomUUID().toString();
        Intent intent = intentClassifier.classify(request.content());
        if (intent != Intent.NEXT_REPAYMENT_QUERY) {
            return new ChatResponse(traceId, intent, "当前纵切仅支持查询下一期账单，请换一种说法或稍后转人工。", null);
        }
        if (request.selectedLoanId() == null || request.selectedLoanId().isBlank()) {
            return new ChatResponse(traceId, intent, "请先选择需要查询的贷款。", null);
        }

        NextRepaymentPlan plan = repaymentTools.getNextRepaymentPlan(request.selectedLoanId());
        // Financial values use a fixed template so the model cannot alter or recompute authoritative results.
        String answer = "贷款%s下一期应还%s元，还款日为%s（本金%s元、利息%s元、费用%s元）。"
                .formatted(plan.loanId(), plan.totalAmount().toPlainString(), plan.dueDate(),
                        plan.principal().toPlainString(), plan.interest().toPlainString(), plan.fee().toPlainString());
        return new ChatResponse(traceId, intent, answer, plan);
    }

    public record ChatRequest(@NotBlank String messageId, @NotBlank String content, String selectedLoanId) {}

    public record ChatResponse(String traceId, Intent intent, String answer, NextRepaymentPlan businessCard) {}
}
