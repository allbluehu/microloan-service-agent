package com.example.microloan.payment;

import java.util.List;

public record PaymentHistory(String loanId, List<PaymentRecord> records) {}
