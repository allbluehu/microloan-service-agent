package com.example.microloan.agent;

import com.example.microloan.payment.PaymentHistory;
import com.example.microloan.payment.PaymentService;
import com.example.microloan.security.CustomerPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PaymentTools {

    private final PaymentService paymentService;

    public PaymentTools(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public PaymentHistory getPaymentRecords(String loanId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomerPrincipal customer)) {
            throw new IllegalStateException("Authenticated customer context is required");
        }
        // 固定最多返回 20 条，限制对话工具的查询与响应体规模。
        return paymentService.getPaymentRecords(customer.customerId(), loanId, null, null, 20);
    }
}
