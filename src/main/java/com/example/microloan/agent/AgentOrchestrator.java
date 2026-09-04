package com.example.microloan.agent;

import com.example.microloan.agent.trace.AgentTraceService;
import com.example.microloan.agent.trace.AgentTraceService.TraceContext;
import com.example.microloan.application.ApplicationAccessDeniedException;
import com.example.microloan.repayment.LoanAccessDeniedException;
import com.example.microloan.settlement.StepUpAuthRequiredException;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
/**
 * 对话应用层编排器，负责确定节点顺序和路由边界，但不实现贷款、支付等领域规则。
 *
 * <p>入口为 {@link #handle}。所有外部输入先绑定认证客户与会话，再经过风险、意图、路由、执行和持久化；
 * 每个阶段同步写入 Agent Trace。业务动作由本类显式选择后调用对应 Tools 包装器，Tools 不是模型函数。</p>
 */
public class AgentOrchestrator {

    private final IntentClassifier intentClassifier;
    private final RiskClassifier riskClassifier;
    private final AgentTraceService traceService;
    private final ChatHistoryService chatHistoryService;
    private final CompositeIntentDetector compositeIntentDetector;
    private final AgentRouteExecutor routeExecutor;
    private final ResultVerifier resultVerifier;
    private final OutputGuardrail outputGuardrail;

    public AgentOrchestrator(IntentClassifier intentClassifier, RiskClassifier riskClassifier,
                             AgentTraceService traceService, ChatHistoryService chatHistoryService,
                             CompositeIntentDetector compositeIntentDetector, AgentRouteExecutor routeExecutor,
                             ResultVerifier resultVerifier, OutputGuardrail outputGuardrail) {
        this.intentClassifier = intentClassifier;
        this.riskClassifier = riskClassifier;
        this.traceService = traceService;
        this.chatHistoryService = chatHistoryService;
        this.compositeIntentDetector = compositeIntentDetector;
        this.routeExecutor = routeExecutor;
        this.resultVerifier = resultVerifier;
        this.outputGuardrail = outputGuardrail;
    }

    /**
     * 执行一轮完整对话：固化会话上下文，依次完成风险识别、意图识别、受控路由和结果持久化。
     * 高风险规则优先于模型意图；模型仅能返回协议内意图，不能直接选择或传入任意业务方法。
     *
     * @param request 客户消息及本轮显式业务槽位
     * @param executionContext 由服务端认证和追踪基础设施生成的可信上下文
     * @return 包含最终意图、结构化业务结果、客户答案和 traceId 的不可变状态
     */
    public AgentState handle(AgentRequest request, AgentExecutionContext executionContext) {
        // 先校验会话归属并合并本轮显式选择，使后续节点使用同一份已持久化上下文快照。
        ChatSessionContext sessionContext = chatHistoryService.recordUserMessage(
                executionContext.customerId(), request.sessionId(), request.messageId(), request.content(),
                request.selectedLoanId(), request.selectedApplicationId(), request.selectedProductCode(),
                request.selectedRegion());
        TraceContext trace = traceService.start(
                executionContext.traceId(), request.sessionId(), executionContext.customerId());
        // AgentState 是不可变的节点载体，避免不同分支原地修改状态导致 Trace 与最终结果不一致。
        AgentState state = new AgentState(executionContext.traceId(), request.sessionId(), request.messageId(),
                request.content(),
                sessionContext.selectedLoanId(), sessionContext.selectedApplicationId(),
                sessionContext.selectedProductCode(), sessionContext.selectedRegion(), request.settlementDate(),
                RiskLevel.LOW, null,
                Intent.UNKNOWN, null, null);
        state = traceRisk(trace, state);
        AgentState completed;
        if (state.riskLevel() == RiskLevel.HIGH) {
            // 高风险接管先于普通意图分类，避免模型把投诉误路由到查询或营销知识。
            AgentState highRiskState = state.withIntent(Intent.HIGH_RISK_COMPLAINT);
            traceRoute(trace, highRiskState);
            completed = traceExecution(trace, highRiskState, () -> routeExecutor.execute(highRiskState));
        } else {
            // 只有低风险消息进入普通意图链；复合意图规则会在模型分类前要求客户澄清。
            state = traceIntent(trace, state);
            traceRoute(trace, state);
            AgentState routedState = state;
            completed = traceExecution(trace, routedState, () -> routeExecutor.execute(routedState));
        }
        completed = traceVerification(trace, completed);
        return finalizeResponse(trace, sessionContext.id(), request.messageId(), completed);
    }

    private AgentState traceVerification(TraceContext trace, AgentState state) {
        long started = System.nanoTime();
        try {
            AgentState verified = resultVerifier.verify(state);
            traceService.success(trace, "RESULT_VERIFIER", "intent=" + state.intent(),
                    "result=VALID", elapsedMs(started));
            return verified;
        } catch (RuntimeException exception) {
            traceService.failed(trace, "RESULT_VERIFIER", "intent=" + state.intent(), exception,
                    elapsedMs(started));
            throw new ToolUnavailableException(exception);
        }
    }

    private AgentState finalizeResponse(TraceContext trace, Long sessionDbId, String messageId,
                                        AgentState completed) {
        long started = System.nanoTime();
        OutputGuardrail.GuardrailResult guarded = outputGuardrail.apply(completed);
        traceService.success(trace, "OUTPUT_GUARDRAIL", "intent=" + completed.intent(),
                "changed=" + guarded.changed() + ",reason=" + guarded.reason(), elapsedMs(started));
        // 最终 HTTP 响应和落库消息复用同一护栏结果，避免只在存储侧遮蔽敏感内容。
        chatHistoryService.recordAssistantMessage(sessionDbId, messageId, guarded.state());
        return guarded.state();
    }

    private AgentState traceRisk(TraceContext trace, AgentState state) {
        long started = System.nanoTime();
        try {
            RiskAssessment assessment = riskClassifier.classify(state.content());
            AgentState result = state.withRisk(assessment);
            traceService.riskSuccess(trace, "messageId=" + state.messageId(), assessment, elapsedMs(started));
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

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
