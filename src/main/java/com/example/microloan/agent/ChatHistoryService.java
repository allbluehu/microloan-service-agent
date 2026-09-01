package com.example.microloan.agent;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ChatHistoryService {
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final Pattern SENSITIVE_DIGITS = Pattern.compile("(?<!\\d)\\d{6,}(?!\\d)");
    private final ChatHistoryMapper mapper;

    public ChatHistoryService(ChatHistoryMapper mapper) {
        this.mapper = mapper;
    }

    public Long recordUserMessage(Long customerId, String sessionId, String messageId, String content) {
        mapper.createSessionIfAbsent(sessionId, customerId);
        Long sessionDbId = mapper.findOwnedSessionId(sessionId, customerId);
        if (sessionDbId == null) {
            // 会话标识已属于其他客户时拒绝复用，避免跨客户消息混入同一上下文。
            throw new IllegalArgumentException("Chat session is not owned by current customer");
        }
        mapper.insertMessage(sessionDbId, messageId, "USER", sanitize(content), null, null);
        mapper.touchSession(sessionDbId);
        return sessionDbId;
    }

    public void recordAssistantMessage(Long sessionDbId, String messageId, AgentState state) {
        mapper.insertMessage(sessionDbId, messageId + ":assistant", "ASSISTANT", sanitize(state.finalAnswer()),
                state.intent().name(), state.traceId());
        mapper.touchSession(sessionDbId);
    }

    public List<ChatMessageView> findRecentMessages(Long customerId, String sessionId, int limit) {
        return List.copyOf(mapper.findRecentOwnedMessages(customerId, sessionId, limit));
    }

    String sanitize(String content) {
        String redacted = SENSITIVE_DIGITS.matcher(content == null ? "" : content).replaceAll("[REDACTED]");
        return redacted.length() <= MAX_CONTENT_LENGTH ? redacted : redacted.substring(0, MAX_CONTENT_LENGTH);
    }
}
