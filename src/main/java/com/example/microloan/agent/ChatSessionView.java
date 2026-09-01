package com.example.microloan.agent;

import java.time.Instant;

public record ChatSessionView(
        /** 客户端使用的会话标识。 */
        String sessionId,
        /** 会话创建时间。 */
        Instant createdAt,
        /** 会话最近消息时间。 */
        Instant updatedAt,
        /** 当前会话已持久化的消息数量。 */
        long messageCount
) {}
