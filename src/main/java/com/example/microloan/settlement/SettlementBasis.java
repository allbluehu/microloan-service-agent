package com.example.microloan.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SettlementBasis(
        String loanNo,
        BigDecimal remainingPrincipal,
        BigDecimal annualRate,
        LocalDate interestAccruedThrough
) {}
