package com.example.microloan.agent;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatHistoryMapper {
    @Insert("INSERT INTO chat_session (session_id, customer_id) VALUES (#{sessionId}, #{customerId}) ON CONFLICT (session_id) DO NOTHING")
    int createSessionIfAbsent(@Param("sessionId") String sessionId, @Param("customerId") Long customerId);

    @Select("SELECT id FROM chat_session WHERE session_id = #{sessionId} AND customer_id = #{customerId}")
    Long findOwnedSessionId(@Param("sessionId") String sessionId, @Param("customerId") Long customerId);

    @Insert("INSERT INTO chat_message (session_id, message_id, role, content, intent, trace_id) VALUES (#{sessionDbId}, #{messageId}, #{role}, #{content}, #{intent}, #{traceId})")
    void insertMessage(@Param("sessionDbId") Long sessionDbId, @Param("messageId") String messageId,
                       @Param("role") String role, @Param("content") String content,
                       @Param("intent") String intent, @Param("traceId") String traceId);

    @Update("UPDATE chat_session SET updated_at = CURRENT_TIMESTAMP WHERE id = #{sessionDbId}")
    void touchSession(@Param("sessionDbId") Long sessionDbId);

    @Select("""
            SELECT m.message_id, m.role, m.content, m.intent, m.trace_id, m.created_at
            FROM chat_message m
            JOIN chat_session s ON s.id = m.session_id
            WHERE s.session_id = #{sessionId} AND s.customer_id = #{customerId}
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT #{limit}
            """)
    List<ChatMessageView> findRecentOwnedMessages(@Param("customerId") Long customerId,
                                                  @Param("sessionId") String sessionId,
                                                  @Param("limit") int limit);

    @Select("""
            SELECT s.session_id, s.created_at, s.updated_at, COUNT(m.id) AS message_count
            FROM chat_session s
            LEFT JOIN chat_message m ON m.session_id = s.id
            WHERE s.customer_id = #{customerId}
            GROUP BY s.id, s.session_id, s.created_at, s.updated_at
            ORDER BY s.updated_at DESC, s.id DESC
            LIMIT #{limit}
            """)
    List<ChatSessionView> findRecentOwnedSessions(@Param("customerId") Long customerId,
                                                  @Param("limit") int limit);
}
