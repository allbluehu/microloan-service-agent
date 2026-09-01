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
}
