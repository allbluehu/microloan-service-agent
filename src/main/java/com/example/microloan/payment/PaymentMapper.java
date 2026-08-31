package com.example.microloan.payment;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentMapper {

    @Select("""
            SELECT COUNT(*)
            FROM loan
            WHERE loan_no = #{loanNo} AND customer_id = #{customerId}
            """)
    int countOwnedLoan(@Param("customerId") Long customerId, @Param("loanNo") String loanNo);

    @Select("""
            <script>
            SELECT p.payment_no AS payment_id, p.amount, p.channel, p.bank_card_last_four,
                   p.status, p.paid_at
            FROM payment_record p
            JOIN loan l ON l.id = p.loan_id
            WHERE l.loan_no = #{loanNo}
              AND l.customer_id = #{customerId}
            <if test="from != null">AND p.paid_at &gt;= #{from}</if>
            <if test="to != null">AND p.paid_at &lt; #{to}</if>
            ORDER BY p.paid_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<PaymentRecord> findPayments(@Param("customerId") Long customerId,
                                     @Param("loanNo") String loanNo,
                                     @Param("from") OffsetDateTime from,
                                     @Param("to") OffsetDateTime to,
                                     @Param("limit") int limit);
}
