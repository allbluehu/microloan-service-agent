package com.example.microloan.repayment;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepaymentService {

    private final RepaymentMapper repaymentMapper;

    public RepaymentService(RepaymentMapper repaymentMapper) {
        this.repaymentMapper = repaymentMapper;
    }

    @Transactional(readOnly = true)
    public NextRepaymentPlan getNextRepaymentPlan(Long authenticatedCustomerId, String loanId) {
        RepaymentPlanRow row = repaymentMapper.findNext(loanId);
        // A missing and a foreign loan share one public error to avoid leaking resource existence.
        if (row == null || !row.customerId().equals(authenticatedCustomerId)) {
            throw new LoanAccessDeniedException();
        }
        BigDecimal total = row.principal().add(row.interest()).add(row.fee());
        return new NextRepaymentPlan(row.loanNo(), row.dueDate(), row.principal(), row.interest(), row.fee(),
                total, row.status());
    }
}
