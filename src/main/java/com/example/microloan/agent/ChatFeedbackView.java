package com.example.microloan.agent;

import java.time.Instant;

public record ChatFeedbackView(
        /** 被评价的客户端消息标识。 */
        String messageId,
        /** 评价方向。 */
        FeedbackRating rating,
        /** 可选的反馈原因。 */
        FeedbackReason reason,
        /** 脱敏后的补充说明。 */
        String comment,
        /** 反馈最近更新时间。 */
        Instant updatedAt
) {}
