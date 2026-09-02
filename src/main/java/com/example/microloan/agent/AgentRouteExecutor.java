package com.example.microloan.agent;

import com.example.microloan.application.ApplicationStatus;
import com.example.microloan.knowledge.KnowledgeAnswer;
import com.example.microloan.knowledge.KnowledgeService;
import com.example.microloan.payment.PaymentHistory;
import com.example.microloan.repayment.NextRepaymentPlan;
import com.example.microloan.settlement.SettlementQuote;
import com.example.microloan.ticket.ServiceTicket;
import com.example.microloan.ticket.TicketPriority;
import com.example.microloan.ticket.TicketType;
import org.springframework.stereotype.Component;

/**
 * 意图到业务动作的确定性白名单路由。模型只能影响 Intent，不能注册、选择或传入任意 Java 方法。
 */
@Component
public class AgentRouteExecutor {

    private final RepaymentTools repaymentTools;
    private final ApplicationTools applicationTools;
    private final PaymentTools paymentTools;
    private final SettlementTools settlementTools;
    private final TicketTools ticketTools;
    private final KnowledgeService knowledgeService;
    private final DeterministicResponseRenderer renderer;

    public AgentRouteExecutor(RepaymentTools repaymentTools, ApplicationTools applicationTools,
                              PaymentTools paymentTools, SettlementTools settlementTools, TicketTools ticketTools,
                              KnowledgeService knowledgeService, DeterministicResponseRenderer renderer) {
        this.repaymentTools = repaymentTools;
        this.applicationTools = applicationTools;
        this.paymentTools = paymentTools;
        this.settlementTools = settlementTools;
        this.ticketTools = ticketTools;
        this.knowledgeService = knowledgeService;
        this.renderer = renderer;
    }

    public AgentState execute(AgentState state) {
        return switch (state.intent()) {
            case NEXT_REPAYMENT_QUERY -> queryNextRepayment(state);
            case APPLICATION_STATUS_QUERY -> queryApplicationStatus(state);
            case PAYMENT_RECORDS_QUERY -> queryPaymentRecords(state);
            case EARLY_SETTLEMENT_QUOTE -> calculateEarlySettlement(state);
            case HIGH_RISK_COMPLAINT -> handoffHighRisk(state);
            case HUMAN_HANDOFF -> handoffRequested(state);
            case KNOWLEDGE_QUERY -> answerKnowledge(state);
            case CLARIFICATION_REQUIRED -> state.complete(
                    "你这一条消息包含多个业务请求。请先选择一个：查询账单、申请进度、还款记录、提前结清试算或产品咨询。");
            case UNKNOWN -> state.complete("当前支持查询下一期账单、贷款申请进度、还款记录和提前结清试算，请补充具体问题。");
        };
    }

    private AgentState queryNextRepayment(AgentState state) {
        if (isBlank(state.selectedLoanId())) {
            return state.complete("请先选择需要查询的贷款。");
        }
        NextRepaymentPlan plan = repaymentTools.getNextRepaymentPlan(state.selectedLoanId());
        return state.complete(new ToolResult.Repayment(plan), renderer.repayment(plan));
    }

    private AgentState queryApplicationStatus(AgentState state) {
        if (isBlank(state.selectedApplicationId())) {
            return state.complete("请先选择需要查询的贷款申请。");
        }
        ApplicationStatus status = applicationTools.getApplicationStatus(state.selectedApplicationId());
        return state.complete(new ToolResult.Application(status), renderer.application(status));
    }

    private AgentState queryPaymentRecords(AgentState state) {
        if (isBlank(state.selectedLoanId())) {
            return state.complete("请先选择需要查询还款记录的贷款。");
        }
        PaymentHistory history = paymentTools.getPaymentRecords(state.selectedLoanId());
        return state.complete(new ToolResult.Payment(history), renderer.payment(history));
    }

    private AgentState calculateEarlySettlement(AgentState state) {
        if (isBlank(state.selectedLoanId())) {
            return state.complete("请先选择需要提前结清试算的贷款。");
        }
        if (state.settlementDate() == null) {
            return state.complete("请提供提前结清的试算日期。");
        }
        SettlementQuote quote = settlementTools.calculateEarlySettlement(
                state.selectedLoanId(), state.settlementDate());
        return state.complete(new ToolResult.Settlement(quote), renderer.settlement(quote, state.settlementDate()));
    }

    private AgentState handoffHighRisk(AgentState state) {
        ServiceTicket ticket = ticketTools.createServiceTicket(state.sessionId(), state.ticketType(),
                state.selectedLoanId(), state.content(), TicketPriority.HIGH);
        return state.complete(new ToolResult.Ticket(ticket), renderer.highRiskTicket(ticket));
    }

    private AgentState handoffRequested(AgentState state) {
        ServiceTicket ticket = ticketTools.createServiceTicket(state.sessionId(), TicketType.GENERAL_COMPLAINT,
                state.selectedLoanId(), state.content(), TicketPriority.NORMAL);
        return state.complete(new ToolResult.Ticket(ticket), renderer.normalTicket(ticket));
    }

    private AgentState answerKnowledge(AgentState state) {
        KnowledgeAnswer answer = knowledgeService.answer(
                state.content(), state.selectedProductCode(), state.selectedRegion());
        return answer.sources().isEmpty()
                ? state.complete(answer.answer())
                : state.complete(new ToolResult.Knowledge(answer), answer.answer());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
