package com.example.microloan.agent;

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
}
