package com.example.microloan.agent;

import java.time.Instant;

public record ChatMessageView(
        /** 客户端消息标识或服务端派生消息标识。 */
        String messageId,
        /** 消息角色：USER 或 ASSISTANT。 */
        String role,
        /** 脱敏并截断后的消息内容。 */
        String content,
        /** Assistant 消息对应的最终意图，用户消息为空。 */
        String intent,
        /** Assistant 消息对应的 Agent Trace 标识，用户消息为空。 */
        String traceId,
        /** 消息写入数据库的时间。 */
        Instant createdAt
) {}
