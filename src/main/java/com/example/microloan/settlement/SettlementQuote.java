package com.example.microloan.settlement;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SettlementQuote(
        /** 本次试算的唯一报价编号。 */
        String quoteId,
        /** 试算对应的贷款编号。 */
        String loanId,
        /** 提前结清时需归还的剩余本金。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal remainingPrincipal,
        /** 计提至结清日的应付利息。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal interest,
        /** 提前结清相关费用。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal fee,
        /** 本金、利息与费用合计。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount,
        /** 本次试算结果的失效时间。 */
        OffsetDateTime validUntil,
        /** 面向客户的试算说明与风险提示。 */
        String notice
) {}
