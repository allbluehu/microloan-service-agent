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
        if ((rating == FeedbackRating.DOWN && reason == null)
                || (rating == FeedbackRating.UP && reason != null)) {
            // 服务层与数据库使用相同规则，避免把约束异常暴露为不可控的基础设施错误。
            throw new IllegalArgumentException("DOWN feedback requires a reason; UP feedback must not include one");
        }
        Long chatMessageId = requireOwnedAssistantMessage(customerId, messageId);
        String safeComment = comment == null || comment.isBlank()
                ? null : SensitiveTextSanitizer.redactAndLimit(comment.strip(), MAX_COMMENT_LENGTH);
        mapper.upsert(chatMessageId, customerId, rating, reason, safeComment);
        return mapper.find(chatMessageId, customerId, messageId);
    }

    public ChatFeedbackView find(Long customerId, String messageId) {
        Long chatMessageId = requireOwnedAssistantMessage(customerId, messageId);
        ChatFeedbackView feedback = mapper.find(chatMessageId, customerId, messageId);
        if (feedback == null) {
            throw new IllegalArgumentException("Feedback has not been submitted for this message");
        }
        return feedback;
    }

    private Long requireOwnedAssistantMessage(Long customerId, String messageId) {
        List<Long> messageIds = mapper.findOwnedAssistantMessageIds(customerId, messageId);
        if (messageIds.size() != 1) {
            // 未找到与歧义使用相同错误，既阻止误绑定，也不泄露其他客户是否拥有该消息。
            throw new IllegalArgumentException("Message is not available for feedback");
        }
        return messageIds.getFirst();
    }
}
