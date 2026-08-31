package com.example.microloan.settlement;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SettlementQuote(
        String quoteId,
        String loanId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal remainingPrincipal,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal interest,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal fee,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount,
        OffsetDateTime validUntil,
        String notice
) {}
