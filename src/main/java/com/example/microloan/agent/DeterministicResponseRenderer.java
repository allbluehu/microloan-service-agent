package com.example.microloan.agent;

import com.example.microloan.application.ApplicationStatus;
import com.example.microloan.payment.PaymentHistory;
import com.example.microloan.repayment.NextRepaymentPlan;
import com.example.microloan.settlement.SettlementQuote;
import com.example.microloan.ticket.ServiceTicket;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 将权威业务结果转换为固定对客文案，不调用生成模型，也不推导或修改金融事实。
 */
@Component
public class DeterministicResponseRenderer {

    public String repayment(NextRepaymentPlan plan) {
        return "贷款%s下一期应还%s元，还款日为%s（本金%s元、利息%s元、费用%s元）。"
                .formatted(plan.loanId(), plan.totalAmount().toPlainString(), plan.dueDate(),
                        plan.principal().toPlainString(), plan.interest().toPlainString(), plan.fee().toPlainString());
    }

    public String application(ApplicationStatus status) {
        String todo = status.missingDocuments().isEmpty()
                ? "当前没有待补资料。"
                : "待补资料：" + String.join("、", status.missingDocuments()) + "。";
        return "申请%s当前状态为%s，更新时间为%s。%s"
                .formatted(status.applicationId(), status.status(), status.updatedAt(), todo);
    }

    public String payment(PaymentHistory history) {
        return history.records().isEmpty()
                ? "未查询到该贷款的还款记录。"
                : "已查询到贷款%s最近%s条还款记录，请查看业务卡片。"
                        .formatted(history.loanId(), history.records().size());
    }

    public String settlement(SettlementQuote quote, LocalDate settlementDate) {
        return "贷款%s于%s提前结清的试算金额为%s元（剩余本金%s元、利息%s元、费用%s元）。%s"
                .formatted(quote.loanId(), settlementDate, quote.totalAmount().toPlainString(),
                        quote.remainingPrincipal().toPlainString(), quote.interest().toPlainString(),
                        quote.fee().toPlainString(), quote.notice());
    }

    public String highRiskTicket(ServiceTicket ticket) {
        return "已为你创建高优先级工单%s，并转交人工客服处理。请勿在聊天中继续发送完整证件号或银行卡号。"
                .formatted(ticket.ticketId());
    }

    public String normalTicket(ServiceTicket ticket) {
        return "已创建工单%s，正在为你转接人工客服。".formatted(ticket.ticketId());
    }
}
