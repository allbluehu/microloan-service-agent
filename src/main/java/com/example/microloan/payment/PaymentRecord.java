package com.example.microloan.payment;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentRecord(
        /** 支付流水对外编号。 */
        String paymentId,
        /** 实际支付金额。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        /** 支付渠道编码。 */
        String channel,
        /** 扣款银行卡后四位。 */
        String bankCardLastFour,
        /** 支付处理状态。 */
        String status,
        /** 支付完成或入账时间。 */
        OffsetDateTime paidAt
) {}
