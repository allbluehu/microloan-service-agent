package com.example.microloan.repayment;

public class RepaymentNotFoundException extends RuntimeException {
    public RepaymentNotFoundException() {
        super("未找到待还账单");
    }
}
