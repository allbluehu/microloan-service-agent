package com.example.microloan.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
                request.selectedLoanId(), request.selectedApplicationId());
        return new ChatResponse(state.traceId(), state.intent(), state.finalAnswer(), state.toolResult());
    }

    public record ChatRequest(@NotBlank String messageId, @NotBlank String content, String selectedLoanId,
                              String selectedApplicationId) {}

    public record ChatResponse(String traceId, Intent intent, String answer, ToolResult businessCard) {}
}
