package com.example.microloan.agent;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatFeedbackService {
    private static final int MAX_COMMENT_LENGTH = 500;
    private final ChatFeedbackMapper mapper;

    public ChatFeedbackService(ChatFeedbackMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public ChatFeedbackView submit(Long customerId, String messageId, FeedbackRating rating,
                                   FeedbackReason reason, String comment) {
        List<Long> messageIds = mapper.findOwnedAssistantMessageIds(customerId, messageId);
        if (messageIds.size() != 1) {
            // 未找到与歧义使用相同错误，既阻止误绑定，也不泄露其他客户是否拥有该消息。
            throw new IllegalArgumentException("Message is not available for feedback");
        }
        Long chatMessageId = messageIds.getFirst();
        String safeComment = comment == null || comment.isBlank()
                ? null : SensitiveTextSanitizer.redactAndLimit(comment.strip(), MAX_COMMENT_LENGTH);
        mapper.upsert(chatMessageId, customerId, rating, reason, safeComment);
        return mapper.find(chatMessageId, customerId, messageId);
    }
}
