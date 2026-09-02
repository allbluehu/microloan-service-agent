package com.example.microloan.agent;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChatHistoryService {
    private static final int MAX_CONTENT_LENGTH = 2000;
    private final ChatHistoryMapper mapper;

    public ChatHistoryService(ChatHistoryMapper mapper) {
        this.mapper = mapper;
    }

    public ChatSessionContext recordUserMessage(Long customerId, String sessionId, String messageId, String content,
                                                String selectedLoanId, String selectedApplicationId,
                                                String selectedProductCode, String selectedRegion) {
        // 先尝试幂等创建，再按 customerId 回查归属；全局相同 sessionId 不能被其他客户复用。
        mapper.createSessionIfAbsent(sessionId, customerId);
        ChatSessionContext context = mapper.findOwnedSessionContext(sessionId, customerId);
        if (context == null) {
            // 会话标识已属于其他客户时拒绝复用，避免跨客户消息混入同一上下文。
            throw new IllegalArgumentException("Chat session is not owned by current customer");
        }
        mapper.updateContext(context.id(), blankToNull(selectedLoanId), blankToNull(selectedApplicationId),
                blankToNull(selectedProductCode), blankToNull(selectedRegion));
        // 回读数据库中的合并结果，保证编排器使用的槽位与实际持久化状态一致。
        ChatSessionContext updated = mapper.findOwnedSessionContext(sessionId, customerId);
        mapper.insertMessage(updated.id(), messageId, "USER", sanitize(content), null, null);
        // 重复请求继续走确定性编排以复用业务工具结果，但唯一约束阻止聊天记录重复增长。
        return updated;
    }

    public void recordAssistantMessage(Long sessionDbId, String messageId, AgentState state) {
        // 使用派生消息标识区分同一轮的用户与助手记录，同时保留数据库唯一约束的幂等保护。
        mapper.insertMessage(sessionDbId, messageId + ":assistant", "ASSISTANT",
                sanitize(state.finalAnswer()), state.intent().name(), state.traceId());
        mapper.touchSession(sessionDbId);
    }

    public List<ChatMessageView> findRecentMessages(Long customerId, String sessionId, int limit) {
        return List.copyOf(mapper.findRecentOwnedMessages(customerId, sessionId, limit));
    }

    public List<ChatSessionView> findRecentSessions(Long customerId, int limit) {
        return List.copyOf(mapper.findRecentOwnedSessions(customerId, limit));
    }

    String sanitize(String content) {
        return SensitiveTextSanitizer.redactAndLimit(content, MAX_CONTENT_LENGTH);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
