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
    // Java 21：每提交一个任务就创建一个虚拟线程，并非复用固定数量的平台线程。
    // 虚拟线程由 JVM 调度，适合这里等待模型 HTTP / 数据库 I/O 的同步业务代码。
    // 它不会让单次调用更快，也不限制任务数量；业务并发上限由下面的信号量控制。
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    // 每轮请求占一个许可，最多允许 20 轮并发，避免大量任务同时压向模型和数据库。
    private final Semaphore capacity = new Semaphore(20);

    public ChatStreamController(AgentOrchestrator orchestrator) { this.orchestrator = orchestrator; }

    /**
     * 建立 SSE 响应，将完整对话交给后台虚拟线程执行。
     * Servlet 请求线程提交任务后返回 emitter，Spring MVC 保持异步响应，由后台继续发送事件。
     * 成功事件为 status → answer → done；answer 是已校验的完整答案，不是逐 Token 输出。
     *
     * @param principal Spring Security 从当前认证中取得的客户身份，不由请求体指定
     * @param sessionId 本轮会话标识，归属由编排器校验
     * @param request 经 Bean Validation 校验的消息及业务选择
     * @return 供 Spring MVC 管理异步 HTTP 响应的事件发送器
     */
    @PostMapping(value="/{sessionId}/stream", produces="text/event-stream")
    public SseEmitter stream(@AuthenticationPrincipal CustomerPrincipal principal,
                             @PathVariable String sessionId,
                             @Valid @RequestBody ChatController.ChatRequest request) throws IOException {
        // tryAcquire 不等待空位：满载时立即返回 HTTP 429，此时尚未建立 SSE 事件流。
        if (!capacity.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "对话繁忙，请稍后重试");
        }
        String traceId = RequestTraceContext.currentOrCreate();
        // 30 秒是 MVC 异步响应超时；它不会自动中断下面的业务任务。
        var emitter = new SseEmitter(30_000L);
        // MVC 回调与业务任务在不同线程访问此标记，AtomicBoolean 保证修改对另一线程可见。
        // 标记只用于尽量停止后续发送；回调触发前仍可能发生网络写入失败。
        var disconnected = new AtomicBoolean();
        emitter.onCompletion(() -> disconnected.set(true));
        emitter.onTimeout(() -> { disconnected.set(true); emitter.complete(); });
        emitter.onError(error -> disconnected.set(true));
        try {
            emitter.send(SseEmitter.event().name("status").data("正在核验并处理请求"));
            // submit 安排后台任务，不等待 handle 返回；lambda 的主体由新虚拟线程执行。
            // 默认 SecurityContext 存在 ThreadLocal 中，新线程不会自动获得请求线程的认证。
            // 此包装器在当前请求线程捕获认证，执行任务时设置到后台线程，结束后清理。
            executor.submit(new DelegatingSecurityContextRunnable(() -> {
                // 认证与日志上下文是两套机制：另行设置 MDC，让后台日志关联同一个 traceId。
                // try-with-resources 在退出时移除此处设置的 MDC 键，避免上下文残留。
                try (var ignored = org.slf4j.MDC.putCloseable("traceId", traceId)) {
                    if (disconnected.get()) return;
                    // 虚拟线程内仍按普通同步调用执行：handle 完成校验和持久化后才返回。
                    // 显式上下文为编排器提供可信客户 ID；SecurityContext 同时供下游认证检查使用。
                    AgentState state = orchestrator.handle(new AgentRequest(sessionId, request.messageId(),
                            request.content(), request.selectedLoanId(), request.selectedApplicationId(),
                            request.selectedProductCode(), request.selectedRegion(), request.settlementDate()),
                            new AgentExecutionContext(principal.customerId(), traceId));
                    // 若处理中连接已结束，跳过发送；已经执行的业务动作不会因此撤销。
                    if (!disconnected.get()) {
                        emitter.send(SseEmitter.event().name("answer").data(new ChatController.ChatResponse(
                                traceId, state.intent(), state.finalAnswer(), state.toolResult())));
                        emitter.send(SseEmitter.event().name("done").data(traceId));
                        // done 是前端识别的业务事件；complete 才通知 Spring MVC 结束响应。
                        emitter.complete();
                    }
                } catch (Exception exception) {
                    // SSE 已开始后，用 error 事件表达失败；不再返回普通 JSON 错误响应。
                    if (!disconnected.get()) {
                        try {
                            emitter.send(SseEmitter.event().name("error").data(error(exception, traceId)));
                            emitter.complete();
                        // 若连错误事件也写不出去，交给 MVC 结束异常响应。
                        } catch (IOException failure) { emitter.completeWithError(failure); }
                    }
                // 即使提前 return 或抛异常也释放许可；断线时仍等任务退出，避免低估业务并发。
                } finally { capacity.release(); }
            }));
        } catch (IOException | RuntimeException exception) {
            // 首个事件发送失败或任务提交失败时，后台 finally 不会执行，由请求线程释放许可。
            capacity.release();
            throw exception;
        }
        // 返回的是发送器而非业务答案；后台任务可能仍在运行，也可能已经完成。
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
    // 容器关闭时停止接收任务并尝试中断运行中的任务；中断并不保证业务立即停止。
    void close() { executor.shutdownNow(); }
}
