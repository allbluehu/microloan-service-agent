package com.example.microloan.ticket;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TicketMapper {

    @Select("""
            SELECT COUNT(*) FROM loan WHERE loan_no = #{loanNo} AND customer_id = #{customerId}
            """)
    int countOwnedLoan(@Param("customerId") Long customerId, @Param("loanNo") String loanNo);

    @Insert("""
            INSERT INTO service_ticket
                (ticket_no, customer_id, conversation_id, related_loan_id, type, priority,
                 summary, status, idempotency_key)
            VALUES
                (#{ticketNo}, #{customerId}, #{conversationId},
                 (SELECT id FROM loan WHERE loan_no = #{loanNo} AND customer_id = #{customerId}),
                 #{type}, #{priority}, #{summary}, 'OPEN', #{idempotencyKey})
            ON CONFLICT (idempotency_key) DO NOTHING
            """)
    int insertIfAbsent(@Param("ticketNo") String ticketNo,
                       @Param("customerId") Long customerId,
                       @Param("conversationId") String conversationId,
                       @Param("loanNo") String loanNo,
                       @Param("type") String type,
                       @Param("priority") String priority,
                       @Param("summary") String summary,
                       @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT st.ticket_no AS ticket_id, st.conversation_id, l.loan_no AS related_loan_id,
                   st.type, st.priority, st.summary, st.status, st.created_at
            FROM service_ticket st
            LEFT JOIN loan l ON l.id = st.related_loan_id
            WHERE st.idempotency_key = #{idempotencyKey}
            """)
    ServiceTicket findByIdempotencyKey(String idempotencyKey);

    @Select("""
            SELECT st.ticket_no AS ticket_id, st.conversation_id, l.loan_no AS related_loan_id,
                   st.type, st.priority, st.summary, st.status, st.created_at
            FROM service_ticket st LEFT JOIN loan l ON l.id = st.related_loan_id
            WHERE st.status = #{status}
            ORDER BY CASE WHEN st.priority = 'HIGH' THEN 0 ELSE 1 END, st.created_at
            LIMIT 100
            """)
    java.util.List<ServiceTicket> findQueue(String status);

    @org.apache.ibatis.annotations.Update("""
            UPDATE service_ticket SET status = #{next}
            WHERE ticket_no = #{ticketId} AND status = #{expected}
            """)
    int transition(@Param("ticketId") String ticketId, @Param("expected") String expected,
                   @Param("next") String next);

    @Select("""
            SELECT m.message_id, m.role, m.content, m.intent, m.trace_id, m.created_at,
                   NULL AS feedback_rating, NULL AS feedback_reason, NULL AS feedback_comment,
                   NULL AS feedback_updated_at
            FROM chat_message m JOIN chat_session s ON s.id = m.session_id
            JOIN service_ticket t ON t.conversation_id = s.session_id AND t.customer_id = s.customer_id
            WHERE t.ticket_no = #{ticketId} ORDER BY m.created_at DESC, m.id DESC LIMIT 100
            """)
    java.util.List<com.example.microloan.agent.ChatMessageView> findContext(String ticketId);

    @Insert("""
            INSERT INTO ticket_action(ticket_no, from_status, to_status, note)
            VALUES (#{ticketId}, #{expected}, #{next}, #{note})
            """)
    int audit(@Param("ticketId") String ticketId, @Param("expected") String expected,
              @Param("next") String next, @Param("note") String note);
}
