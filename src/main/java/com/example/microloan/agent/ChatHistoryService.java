package com.example.microloan.agent;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * 会话、消息和跨轮槽位的应用服务。
 *
 * <p>AgentOrchestrator 在分类前调用 {@link #recordUserMessage}，取得数据库确认后的上下文；执行完成后
 * 调用 {@link #recordAssistantMessage}。所有查询都携带认证 customerId，消息正文写库前统一脱敏限长。</p>
 */
public class ChatHistoryService {
    private static final int MAX_CONTENT_LENGTH = 2000;
    private final ChatHistoryMapper mapper;

    public ChatHistoryService(ChatHistoryMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 幂等创建/校验会话、合并本轮显式槽位并保存客户消息。
     * 空白槽位不会清除历史选择；返回值是回读后的数据库快照，供本轮所有后续节点共同使用。
     */
    @Transactional
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

    /** 在独立短事务中保存护栏处理后的最终答复并刷新会话时间。 */
    @Transactional
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
