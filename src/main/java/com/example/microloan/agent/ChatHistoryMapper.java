package com.example.microloan.agent;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
/**
 * 会话与消息 SQL 边界，由 ChatHistoryService 独占调用。
 * 所有读取方法必须同时使用 customerId 和业务标识限制归属，不能先按 sessionId 查询后在 Java 中过滤。
 */
public interface ChatHistoryMapper {
    @Insert("INSERT INTO chat_session (session_id, customer_id) VALUES (#{sessionId}, #{customerId}) ON CONFLICT (session_id) DO NOTHING")
    int createSessionIfAbsent(@Param("sessionId") String sessionId, @Param("customerId") Long customerId);

    @Select("""
            SELECT id, selected_loan_id, selected_application_id, selected_product_code, selected_region
            FROM chat_session
            WHERE session_id = #{sessionId} AND customer_id = #{customerId}
            """)
    ChatSessionContext findOwnedSessionContext(@Param("sessionId") String sessionId,
                                               @Param("customerId") Long customerId);

    @Update("""
            UPDATE chat_session
            SET selected_loan_id = COALESCE(#{selectedLoanId}, selected_loan_id),
                selected_application_id = COALESCE(#{selectedApplicationId}, selected_application_id),
                selected_product_code = COALESCE(#{selectedProductCode}, selected_product_code),
                selected_region = COALESCE(#{selectedRegion}, selected_region),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{sessionDbId}
            """)
    void updateContext(@Param("sessionDbId") Long sessionDbId,
                       @Param("selectedLoanId") String selectedLoanId,
                       @Param("selectedApplicationId") String selectedApplicationId,
                       @Param("selectedProductCode") String selectedProductCode,
                       @Param("selectedRegion") String selectedRegion);

    @Insert("""
            INSERT INTO chat_message (session_id, message_id, role, content, intent, trace_id)
            VALUES (#{sessionDbId}, #{messageId}, #{role}, #{content}, #{intent}, #{traceId})
            ON CONFLICT (session_id, message_id, role) DO NOTHING
            """)
    int insertMessage(@Param("sessionDbId") Long sessionDbId, @Param("messageId") String messageId,
                      @Param("role") String role, @Param("content") String content,
                      @Param("intent") String intent, @Param("traceId") String traceId);

    @Update("UPDATE chat_session SET updated_at = CURRENT_TIMESTAMP WHERE id = #{sessionDbId}")
    void touchSession(@Param("sessionDbId") Long sessionDbId);

    @Select("""
            SELECT m.message_id, m.role, m.content, m.intent, m.trace_id, m.created_at,
                   f.rating AS feedback_rating, f.reason AS feedback_reason,
                   f.comment AS feedback_comment, f.updated_at AS feedback_updated_at
            FROM chat_message m
            JOIN chat_session s ON s.id = m.session_id
            LEFT JOIN chat_feedback f
              ON f.chat_message_id = m.id AND f.customer_id = s.customer_id
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

    @Update("""
            UPDATE chat_session SET selected_loan_id = NULL, selected_application_id = NULL,
                selected_product_code = NULL, selected_region = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE session_id = #{sessionId} AND customer_id = #{customerId}
            """)
    int clearOwnedContext(@Param("customerId") Long customerId, @Param("sessionId") String sessionId);
}
