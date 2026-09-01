package com.example.microloan.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SettlementBasis(
        /** 试算对应的贷款编号。 */
        String loanNo,
        /** 试算时点的剩余本金。 */
        BigDecimal remainingPrincipal,
        /** 合同约定年化利率。 */
        BigDecimal annualRate,
        /** 数据库中利息已计提至的日期。 */
        LocalDate interestAccruedThrough
) {}
