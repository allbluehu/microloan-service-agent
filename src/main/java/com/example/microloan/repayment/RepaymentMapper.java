package com.example.microloan.repayment;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RepaymentMapper {

    @Select("""
            SELECT l.loan_no, l.customer_id, rp.due_date, rp.principal, rp.interest, rp.fee, rp.status
            FROM loan l
            JOIN repayment_plan rp ON rp.loan_id = l.id
            WHERE l.loan_no = #{loanNo}
              AND rp.status = 'PENDING'
            ORDER BY rp.due_date, rp.period_no
            LIMIT 1
            """)
    RepaymentPlanRow findNext(@Param("loanNo") String loanNo);
}
