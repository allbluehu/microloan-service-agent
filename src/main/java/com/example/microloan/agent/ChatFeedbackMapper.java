package com.example.microloan.agent;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
/**
 * 回答反馈持久化接口。
 * 写入和读取均通过消息与会话关联校验 customerId，防止客户对其他人的消息提交或读取反馈。
 */
public interface ChatFeedbackMapper {

    @Select("""
            SELECT m.id
            FROM chat_message m
            JOIN chat_session s ON s.id = m.session_id
            WHERE s.customer_id = #{customerId}
              AND m.role = 'ASSISTANT'
              AND m.message_id = CONCAT(#{messageId}, ':assistant')
            """)
    List<Long> findOwnedAssistantMessageIds(@Param("customerId") Long customerId,
                                            @Param("messageId") String messageId);

    @Insert("""
            INSERT INTO chat_feedback (chat_message_id, customer_id, rating, reason, comment)
            VALUES (#{chatMessageId}, #{customerId}, #{rating}, #{reason}, #{comment})
            ON CONFLICT (chat_message_id) DO UPDATE
            SET rating = EXCLUDED.rating,
                reason = EXCLUDED.reason,
                comment = EXCLUDED.comment,
                updated_at = CURRENT_TIMESTAMP
            WHERE chat_feedback.customer_id = EXCLUDED.customer_id
            """)
    void upsert(@Param("chatMessageId") Long chatMessageId,
                @Param("customerId") Long customerId,
                @Param("rating") FeedbackRating rating,
                @Param("reason") FeedbackReason reason,
                @Param("comment") String comment);

    @Select("""
            SELECT #{messageId} AS message_id, rating, reason, comment, updated_at
            FROM chat_feedback
            WHERE chat_message_id = #{chatMessageId} AND customer_id = #{customerId}
            """)
    ChatFeedbackView find(@Param("chatMessageId") Long chatMessageId,
                          @Param("customerId") Long customerId,
                          @Param("messageId") String messageId);
}
