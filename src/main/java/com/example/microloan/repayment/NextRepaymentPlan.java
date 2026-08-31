package com.example.microloan.repayment;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

public record NextRepaymentPlan(
        String loanId,
        LocalDate dueDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal principal,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal interest,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal fee,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal totalAmount,
        String status
) {}
