package com.example.microloan.agent;

import com.example.microloan.api.ApiError;
import com.example.microloan.api.RequestTraceContext;
import com.example.microloan.security.CustomerPrincipal;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** SSE 只发送完成结果校验后的答案，不把未校验模型片段暴露给客户。 */
@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatStreamController {
    private final AgentOrchestrator orchestrator;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore capacity = new Semaphore(20);

    public ChatStreamController(AgentOrchestrator orchestrator) { this.orchestrator = orchestrator; }

    @PostMapping(value="/{sessionId}/stream", produces="text/event-stream")
    public SseEmitter stream(@AuthenticationPrincipal CustomerPrincipal principal,
                             @PathVariable String sessionId,
                             @Valid @RequestBody ChatController.ChatRequest request) throws IOException {
        if (!capacity.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "对话繁忙，请稍后重试");
        }
        String traceId = RequestTraceContext.currentOrCreate();
        var emitter = new SseEmitter(30_000L);
        var disconnected = new AtomicBoolean();
        emitter.onCompletion(() -> disconnected.set(true));
        emitter.onTimeout(() -> { disconnected.set(true); emitter.complete(); });
        emitter.onError(error -> disconnected.set(true));
        try {
            emitter.send(SseEmitter.event().name("status").data("正在核验并处理请求"));
            // 显式传递客户认证和追踪标识；虚拟线程不继承 Servlet 线程的身份。
            executor.submit(new DelegatingSecurityContextRunnable(() -> {
                try (var ignored = org.slf4j.MDC.putCloseable("traceId", traceId)) {
                    if (disconnected.get()) return;
                    AgentState state = orchestrator.handle(new AgentRequest(sessionId, request.messageId(),
                            request.content(), request.selectedLoanId(), request.selectedApplicationId(),
                            request.selectedProductCode(), request.selectedRegion(), request.settlementDate()),
                            new AgentExecutionContext(principal.customerId(), traceId));
                    if (!disconnected.get()) {
                        emitter.send(SseEmitter.event().name("answer").data(new ChatController.ChatResponse(
                                traceId, state.intent(), state.finalAnswer(), state.toolResult())));
                        emitter.send(SseEmitter.event().name("done").data(traceId));
                        emitter.complete();
                    }
                } catch (Exception exception) {
                    if (!disconnected.get()) {
                        try {
                            emitter.send(SseEmitter.event().name("error").data(error(exception, traceId)));
                            emitter.complete();
                        } catch (IOException failure) { emitter.completeWithError(failure); }
                    }
                } finally { capacity.release(); }
            }));
        } catch (IOException | RuntimeException exception) {
            capacity.release();
            throw exception;
        }
        return emitter;
    }

    private ApiError error(Exception exception, String traceId) {
        String code = "TOOL_UNAVAILABLE";
        String message = "服务暂不可用，请稍后重试";
        if (exception instanceof com.example.microloan.settlement.StepUpAuthRequiredException) {
            code = "STEP_UP_AUTH_REQUIRED"; message = "请完成二次认证后重试";
        } else if (exception instanceof com.example.microloan.repayment.LoanAccessDeniedException
                || exception instanceof com.example.microloan.application.ApplicationAccessDeniedException) {
            code = "ACCESS_DENIED"; message = "没有访问该资源的权限";
        } else if (exception instanceof IllegalArgumentException) {
            code = "INVALID_ARGUMENT"; message = "请检查会话归属及请求参数";
        }
        return new ApiError(code, message, traceId);
    }

    @PreDestroy
    void close() { executor.shutdownNow(); }
}
