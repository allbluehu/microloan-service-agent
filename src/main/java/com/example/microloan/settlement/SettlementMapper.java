package com.example.microloan.settlement;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SettlementMapper {

    @Select("""
            SELECT loan_no, remaining_principal, annual_rate, interest_accrued_through
            FROM loan
            WHERE loan_no = #{loanNo}
              AND customer_id = #{customerId}
              AND status = 'ACTIVE'
            """)
    SettlementBasis findBasis(@Param("customerId") Long customerId, @Param("loanNo") String loanNo);
}
