package com.example.microloan.agent;

import com.example.microloan.payment.PaymentHistory;
import com.example.microloan.payment.PaymentService;
import com.example.microloan.security.CustomerPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
/**
 * 编排器与支付记录领域服务之间的窄适配层。
 * 返回的是已限制范围并脱敏的 PaymentHistory，模型不参与银行卡信息裁剪。
 */
public class PaymentTools {

    private final PaymentService paymentService;

    public PaymentTools(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 查询认证客户所选贷款的有限条数还款记录。 */
    public PaymentHistory getPaymentRecords(String loanId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomerPrincipal customer)) {
            throw new IllegalStateException("Authenticated customer context is required");
        }
        // 固定最多返回 20 条，限制对话工具的查询与响应体规模。
        return paymentService.getPaymentRecords(customer.customerId(), loanId, null, null, 20);
    }
}
