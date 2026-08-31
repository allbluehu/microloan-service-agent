package com.example.microloan.payment;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentRecord(
        String paymentId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String channel,
        String bankCardLastFour,
        String status,
        OffsetDateTime paidAt
) {}
