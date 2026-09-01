package com.example.microloan.agent;

import com.example.microloan.repayment.NextRepaymentPlan;
import com.example.microloan.repayment.RepaymentService;
import com.example.microloan.security.CustomerPrincipal;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class RepaymentTools {

    private final RepaymentService repaymentService;

    public RepaymentTools(RepaymentService repaymentService) {
        this.repaymentService = repaymentService;
    }

    @Tool(description = "查询已登录客户指定贷款的下一期待还金额和日期")
    public NextRepaymentPlan getNextRepaymentPlan(String loanId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomerPrincipal customer)) {
            throw new IllegalStateException("Authenticated customer context is required");
        }
        // 客户身份只取自 SecurityContext，绝不接受模型可控参数，防止横向越权查询。
        return repaymentService.getNextRepaymentPlan(customer.customerId(), loanId);
    }
}
