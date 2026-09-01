package com.example.microloan.repayment;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

public record NextRepaymentPlan(
        /** 还款计划所属贷款编号。 */
        String loanId,
        /** 下一期计划还款日。 */
        LocalDate dueDate,
        /** 下一期应还本金。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal principal,
        /** 下一期应还利息。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal interest,
        /** 下一期应还费用。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal fee,
        /** 本金、利息与费用合计。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal totalAmount,
        /** 下一期还款计划状态。 */
        String status
) {}
