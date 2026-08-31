package com.example.microloan.agent;

import com.example.microloan.payment.PaymentHistory;
import com.example.microloan.payment.PaymentService;
import com.example.microloan.security.CustomerPrincipal;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PaymentTools {

    private final PaymentService paymentService;

    public PaymentTools(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Tool(description = "查询已登录客户指定贷款最近的还款记录，最多返回20条")
    public PaymentHistory getPaymentRecords(String loanId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomerPrincipal customer)) {
            throw new IllegalStateException("Authenticated customer context is required");
        }
        return paymentService.getPaymentRecords(customer.customerId(), loanId, null, null, 20);
    }
}
