package com.example.microloan.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatController {

    private final AgentOrchestrator orchestrator;

    public ChatController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/{sessionId}/messages")
    public ChatResponse send(@PathVariable String sessionId, @Valid @RequestBody ChatRequest request) {
        AgentState state = orchestrator.handle(sessionId, request.messageId(), request.content(),
                request.selectedLoanId(), request.selectedApplicationId(), request.selectedProductCode(),
                request.selectedRegion(), request.settlementDate());
        return new ChatResponse(state.traceId(), state.intent(), state.finalAnswer(), state.toolResult());
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
