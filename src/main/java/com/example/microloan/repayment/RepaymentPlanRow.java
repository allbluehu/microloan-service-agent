package com.example.microloan.repayment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RepaymentPlanRow(
        String loanNo,
        Long customerId,
        LocalDate dueDate,
        BigDecimal principal,
        BigDecimal interest,
        BigDecimal fee,
        String status
) {}
