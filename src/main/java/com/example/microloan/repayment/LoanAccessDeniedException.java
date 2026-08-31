package com.example.microloan.repayment;

public class LoanAccessDeniedException extends RuntimeException {
    public LoanAccessDeniedException() {
        super("无法访问该贷款信息");
    }
}
