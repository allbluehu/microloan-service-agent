package com.example.microloan.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import com.example.microloan.api.RequestTraceContext;
import com.example.microloan.security.CustomerPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/sessions")
@Validated
/**
 * 客户对话 HTTP 接口。
 *
 * <p>发送消息调用 {@link AgentOrchestrator#handle} 完成整轮编排；历史消息和会话列表直接调用
 * {@link ChatHistoryService}，后者在 SQL 层始终附带当前 customerId，防止通过 sessionId 横向读取。</p>
 */
public class ChatController {

    private final AgentOrchestrator orchestrator;
    private final ChatHistoryService chatHistoryService;

    public ChatController(AgentOrchestrator orchestrator, ChatHistoryService chatHistoryService) {
        this.orchestrator = orchestrator;
        this.chatHistoryService = chatHistoryService;
    }

    @PostMapping("/{sessionId}/messages")
    /** 提交一轮对话；业务失败由统一异常处理器转换为稳定 API 错误契约。 */
    public ChatResponse send(@AuthenticationPrincipal CustomerPrincipal principal,
                             @PathVariable String sessionId, @Valid @RequestBody ChatRequest request) {
        // customerId 和 traceId 均来自服务端上下文；请求体和模型不能覆盖可信执行身份。
        AgentRequest command = new AgentRequest(sessionId, request.messageId(), request.content(),
                request.selectedLoanId(), request.selectedApplicationId(), request.selectedProductCode(),
                request.selectedRegion(), request.settlementDate());
        AgentExecutionContext context = new AgentExecutionContext(
                principal.customerId(), RequestTraceContext.currentOrCreate());
        AgentState state = orchestrator.handle(command, context);
        return new ChatResponse(state.traceId(), state.intent(), state.finalAnswer(), state.toolResult());
    }

    @GetMapping("/{sessionId}/messages")
    /** 查询当前客户指定会话的最近消息，返回结果同时包含已提交的反馈。 */
    public List<ChatMessageView> messages(@AuthenticationPrincipal CustomerPrincipal principal,
                                          @PathVariable String sessionId,
                                          @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return chatHistoryService.findRecentMessages(principal.customerId(), sessionId, limit);
    }

    @GetMapping
    /** 查询当前客户最近更新的会话摘要，不接受请求方传入 customerId。 */
    public List<ChatSessionView> sessions(@AuthenticationPrincipal CustomerPrincipal principal,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return chatHistoryService.findRecentSessions(principal.customerId(), limit);
    }

    public record ChatRequest(
            /** 客户端生成的消息标识。 */
            @NotBlank String messageId,
            /** 客户输入的自然语言内容。 */
            @NotBlank String content,
            /** 当前选中的贷款编号，可为空。 */
            String selectedLoanId,
            /** 当前选中的申请编号，可为空。 */
            String selectedApplicationId,
            /** 当前选中的产品编码，可为空。 */
            String selectedProductCode,
            /** 当前选中的地区编码，可为空。 */
            String selectedRegion,
            /** 提前结清试算日期，可为空。 */
            LocalDate settlementDate
    ) {}

    public record ChatResponse(
            /** 本轮请求的追踪标识。 */
            String traceId,
            /** Agent 最终采用的意图。 */
            Intent intent,
            /** 面向客户的文本答案。 */
            String answer,
            /** 可选的结构化业务卡片。 */
            ToolResult businessCard
    ) {}
}
