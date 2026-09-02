package com.example.microloan.agent;

import com.example.microloan.repayment.NextRepaymentPlan;
import com.example.microloan.repayment.RepaymentService;
import com.example.microloan.security.CustomerPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
/**
 * 编排器与还款领域服务之间的窄适配层。
 * 仅由 AgentOrchestrator 的 NEXT_REPAYMENT_QUERY 白名单分支调用；贷款归属校验仍由 RepaymentService 执行。
 */
public class RepaymentTools {

    private final RepaymentService repaymentService;

    public RepaymentTools(RepaymentService repaymentService) {
        this.repaymentService = repaymentService;
    }

    /** 查询认证客户所选贷款的下一期计划，不接受模型提供客户身份。 */
    public NextRepaymentPlan getNextRepaymentPlan(String loanId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomerPrincipal customer)) {
            throw new IllegalStateException("Authenticated customer context is required");
        }
        // 客户身份只取自 SecurityContext，绝不接受模型可控参数，防止横向越权查询。
        return repaymentService.getNextRepaymentPlan(customer.customerId(), loanId);
    }
}
