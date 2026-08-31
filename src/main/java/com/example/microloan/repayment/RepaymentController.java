package com.example.microloan.repayment;

import com.example.microloan.security.CustomerPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
public class RepaymentController {

    private final RepaymentService repaymentService;

    public RepaymentController(RepaymentService repaymentService) {
        this.repaymentService = repaymentService;
    }

    @GetMapping("/{loanId}/next-repayment")
    public NextRepaymentPlan getNext(@AuthenticationPrincipal CustomerPrincipal principal,
                                     @PathVariable String loanId) {
        return repaymentService.getNextRepaymentPlan(principal.customerId(), loanId);
    }
}
