package com.example.microloan.payment;

import java.util.List;

public record PaymentHistory(
        /** 还款记录所属贷款编号。 */
        String loanId,
        /** 按业务规则排序的还款记录。 */
        List<PaymentRecord> records
) {}
