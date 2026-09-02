package com.example.microloan.agent;

import com.example.microloan.agent.trace.AgentTraceService;
import com.example.microloan.agent.trace.AgentTraceService.TraceContext;
import com.example.microloan.api.RequestTraceContext;
import com.example.microloan.application.ApplicationStatus;
import com.example.microloan.application.ApplicationAccessDeniedException;
import com.example.microloan.knowledge.KnowledgeAnswer;
import com.example.microloan.knowledge.KnowledgeService;
import com.example.microloan.payment.PaymentHistory;
import com.example.microloan.repayment.NextRepaymentPlan;
import com.example.microloan.repayment.LoanAccessDeniedException;
import com.example.microloan.settlement.SettlementQuote;
import com.example.microloan.settlement.StepUpAuthRequiredException;
import com.example.microloan.security.CustomerPrincipal;
import com.example.microloan.ticket.ServiceTicket;
import com.example.microloan.ticket.TicketPriority;
import com.example.microloan.ticket.TicketType;
import java.time.LocalDate;
import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentOrchestrator {

    private final IntentClassifier intentClassifier;
    private final RepaymentTools repaymentTools;
    private final ApplicationTools applicationTools;
    private final PaymentTools paymentTools;
    private final SettlementTools settlementTools;
    private final RiskClassifier riskClassifier;
    private final TicketTools ticketTools;
    private final AgentTraceService traceService;
    private final KnowledgeService knowledgeService;
    private final ChatHistoryService chatHistoryService;
    private final CompositeIntentDetector compositeIntentDetector;

    public AgentOrchestrator(IntentClassifier intentClassifier, RepaymentTools repaymentTools,
                             ApplicationTools applicationTools, PaymentTools paymentTools,
                             SettlementTools settlementTools, RiskClassifier riskClassifier,
                             TicketTools ticketTools, AgentTraceService traceService,
                             KnowledgeService knowledgeService, ChatHistoryService chatHistoryService,
                             CompositeIntentDetector compositeIntentDetector) {
        this.intentClassifier = intentClassifier;
        this.repaymentTools = repaymentTools;
        this.applicationTools = applicationTools;
        this.paymentTools = paymentTools;
        this.settlementTools = settlementTools;
        this.riskClassifier = riskClassifier;
        this.ticketTools = ticketTools;
        this.traceService = traceService;
        this.knowledgeService = knowledgeService;
        this.chatHistoryService = chatHistoryService;
        this.compositeIntentDetector = compositeIntentDetector;
    }

    /**
     * 执行一轮完整对话：固化会话上下文，依次完成风险识别、意图识别、受控路由和结果持久化。
     * 高风险规则优先于模型意图；模型仅能返回协议内意图，不能直接选择或传入任意业务方法。
     */
    @Transactional
    public AgentState handle(String sessionId, String messageId, String content, String selectedLoanId,
                             String selectedApplicationId, String selectedProductCode, String selectedRegion,
                             LocalDate settlementDate) {
        // HTTP 入口复用请求级 traceId，使响应头、日志、Agent Trace 和聊天记录可按同一标识关联。
        String traceId = RequestTraceContext.currentOrCreate();
        // 客户身份只取自服务端安全上下文，禁止使用请求或模型生成的客户标识。
        CustomerPrincipal principal = authenticatedPrincipal();
        // 先校验会话归属并合并本轮显式选择，使后续节点使用同一份已持久化上下文快照。
        ChatSessionContext sessionContext = chatHistoryService.recordUserMessage(
                principal.customerId(), sessionId, messageId, content, selectedLoanId, selectedApplicationId,
                selectedProductCode, selectedRegion);
        TraceContext trace = traceService.start(traceId, sessionId, principal.customerId());
        // AgentState 是不可变的节点载体，避免不同分支原地修改状态导致 Trace 与最终结果不一致。
        AgentState state = new AgentState(traceId, sessionId, messageId, content,
                sessionContext.selectedLoanId(), sessionContext.selectedApplicationId(),
                sessionContext.selectedProductCode(), sessionContext.selectedRegion(), settlementDate,
                RiskLevel.LOW, null,
                Intent.UNKNOWN, null, null);
        state = traceRisk(trace, state);
        if (state.riskLevel() == RiskLevel.HIGH) {
            // 高风险接管先于普通意图分类，避免模型把投诉误路由到查询或营销知识。
            AgentState highRiskState = state.withIntent(Intent.HIGH_RISK_COMPLAINT);
            traceRoute(trace, highRiskState);
            AgentState completed = traceExecution(trace, highRiskState, () -> handoffHighRisk(highRiskState));
            chatHistoryService.recordAssistantMessage(sessionContext.id(), messageId, completed);
            return completed;
        }
        // 只有低风险消息进入普通意图链；复合意图规则会在模型分类前要求客户澄清。
        state = traceIntent(trace, state);
        traceRoute(trace, state);
        AgentState routedState = state;

        // switch 是确定性路由边界：模型输出只能映射到以下白名单分支，不能执行任意应用代码。
        AgentState completed = traceExecution(trace, routedState, () -> switch (routedState.intent()) {
            case NEXT_REPAYMENT_QUERY -> queryNextRepayment(routedState);
            case APPLICATION_STATUS_QUERY -> queryApplicationStatus(routedState);
            case PAYMENT_RECORDS_QUERY -> queryPaymentRecords(routedState);
            case EARLY_SETTLEMENT_QUOTE -> calculateEarlySettlement(routedState);
            case HIGH_RISK_COMPLAINT -> handoffHighRisk(routedState);
            case HUMAN_HANDOFF -> handoffRequested(routedState);
            case KNOWLEDGE_QUERY -> answerKnowledge(routedState);
            case CLARIFICATION_REQUIRED -> routedState.complete(
                    "你这一条消息包含多个业务请求。请先选择一个：查询账单、申请进度、还款记录、提前结清试算或产品咨询。");
            case UNKNOWN -> routedState.complete("当前支持查询下一期账单、贷款申请进度、还款记录和提前结清试算，请补充具体问题。");
        });
        // 助手消息与本轮业务执行处于同一事务；失败时不留下只有半边的对话记录。
        chatHistoryService.recordAssistantMessage(sessionContext.id(), messageId, completed);
        return completed;
    }

    private AgentState traceRisk(TraceContext trace, AgentState state) {
        long started = System.nanoTime();
        try {
            AgentState result = state.withRisk(riskClassifier.classify(state.content()));
            traceService.success(trace, "RISK_CLASSIFIER", "messageId=" + state.messageId(),
                    "riskLevel=" + result.riskLevel() + ",ticketType=" + result.ticketType(), elapsedMs(started));
            return result;
        } catch (RuntimeException exception) {
            traceService.failed(trace, "RISK_CLASSIFIER", "messageId=" + state.messageId(), exception,
                    elapsedMs(started));
            throw exception;
        }
    }

    private AgentState traceIntent(TraceContext trace, AgentState state) {
        long started = System.nanoTime();
        try {
            // 多个普通业务动作不猜测执行顺序，避免一次消息触发客户未确认的组合操作。
            if (compositeIntentDetector.requiresClarification(state.content())) {
                AgentState result = state.withIntent(Intent.CLARIFICATION_REQUIRED);
                traceService.intentSuccess(trace, "messageId=" + state.messageId(),
                        "intent=" + result.intent() + ",confidence=1.0,source=COMPOSITE_RULE", false,
                        elapsedMs(started));
                return result;
            }
            IntentClassifier.ClassificationResult classification = intentClassifier.classifyDetailed(state.content());
            AgentState result = state.withIntent(classification.intent());
            traceService.intentSuccess(trace, "messageId=" + state.messageId(),
                    "intent=" + result.intent() + ",confidence=" + classification.confidence()
                            + ",source=" + classification.source(),
                    classification.source() == IntentClassifier.ClassificationSource.MODEL, elapsedMs(started));
            return result;
        } catch (RuntimeException exception) {
            traceService.failed(trace, "INTENT_CLASSIFIER", "messageId=" + state.messageId(), exception,
                    elapsedMs(started));
            throw exception;
        }
    }

    private void traceRoute(TraceContext trace, AgentState state) {
        traceService.success(trace, "ROUTE_DECISION", "intent=" + state.intent(),
                "route=" + state.intent(), 0);
    }

    private AgentState traceExecution(TraceContext trace, AgentState state, Supplier<AgentState> execution) {
        // 知识检索与业务工具使用不同节点名，便于分别统计召回质量和工具可靠性。
        String nodeName = state.intent() == Intent.KNOWLEDGE_QUERY ? "KNOWLEDGE_RETRIEVER" : "TOOL_EXECUTOR";
        long started = System.nanoTime();
        try {
            AgentState result = execution.get();
            String resultType = result.toolResult() == null ? "NONE" : result.toolResult().getClass().getSimpleName();
            traceService.success(trace, nodeName, "intent=" + state.intent(),
                    "resultType=" + resultType, elapsedMs(started));
            return result;
        } catch (RuntimeException exception) {
            traceService.failed(trace, nodeName, "intent=" + state.intent(), exception,
                    elapsedMs(started));
            if (isExpectedBusinessRejection(exception)) {
                throw exception;
            }
            // 未知基础设施或工具故障统一转换，避免将内部异常细节暴露给客户。
            throw new ToolUnavailableException(exception);
        }
    }

    private boolean isExpectedBusinessRejection(RuntimeException exception) {
        return exception instanceof LoanAccessDeniedException
                || exception instanceof ApplicationAccessDeniedException
                || exception instanceof StepUpAuthRequiredException
                || exception instanceof IllegalArgumentException;
    }

    private CustomerPrincipal authenticatedPrincipal() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomerPrincipal customer) {
            return customer;
        }
        throw new IllegalStateException("Authenticated customer context is required");
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private AgentState queryNextRepayment(AgentState state) {
        if (isBlank(state.selectedLoanId())) {
            // 缺少资源标识时只澄清，不让模型猜测客户要查询哪一笔贷款。
            return state.complete("请先选择需要查询的贷款。");
        }
        NextRepaymentPlan plan = repaymentTools.getNextRepaymentPlan(state.selectedLoanId());
        // 金融数值使用固定模板呈现，避免模型改写权威工具返回的金额和日期。
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

    private AgentState handoffHighRisk(AgentState state) {
        ServiceTicket ticket = ticketTools.createServiceTicket(state.sessionId(), state.ticketType(),
                state.selectedLoanId(), state.content(), TicketPriority.HIGH);
        String answer = "已为你创建高优先级工单%s，并转交人工客服处理。请勿在聊天中继续发送完整证件号或银行卡号。"
                .formatted(ticket.ticketId());
        return state.complete(new ToolResult.Ticket(ticket), answer);
    }

    private AgentState handoffRequested(AgentState state) {
        ServiceTicket ticket = ticketTools.createServiceTicket(state.sessionId(), TicketType.GENERAL_COMPLAINT,
                state.selectedLoanId(), state.content(), TicketPriority.NORMAL);
        return state.complete(new ToolResult.Ticket(ticket), "已创建工单%s，正在为你转接人工客服。"
                .formatted(ticket.ticketId()));
    }

    private AgentState answerKnowledge(AgentState state) {
        KnowledgeAnswer answer = knowledgeService.answer(
                state.content(), state.selectedProductCode(), state.selectedRegion());
        if (answer.sources().isEmpty()) {
            // 无可靠来源时仅返回知识服务的拒答文本，不构造看似有依据的业务卡片。
            return state.complete(answer.answer());
        }
        return state.complete(new ToolResult.Knowledge(answer), answer.answer());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
