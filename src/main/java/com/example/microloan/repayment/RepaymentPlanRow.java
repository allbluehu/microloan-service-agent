package com.example.microloan.repayment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RepaymentPlanRow(
        /** 还款计划所属贷款编号。 */
        String loanNo,
        /** 贷款所属客户主键。 */
        Long customerId,
        /** 本期计划还款日。 */
        LocalDate dueDate,
        /** 本期应还本金。 */
        BigDecimal principal,
        /** 本期应还利息。 */
        BigDecimal interest,
        /** 本期应还费用。 */
        BigDecimal fee,
        /** 还款计划状态。 */
        String status
) {}
