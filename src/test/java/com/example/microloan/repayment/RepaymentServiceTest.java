package com.example.microloan.repayment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RepaymentServiceTest {

    @Test
    void returnsAuthoritativePlanAndCalculatesTotalInJava() {
        RepaymentPlanRow row = new RepaymentPlanRow(
                "L10001", 10001L, LocalDate.of(2026, 9, 15),
                new BigDecimal("1200.00"), new BigDecimal("62.50"), new BigDecimal("0.00"), "PENDING");
        RepaymentMapper repaymentMapper = loanNo -> row;

        NextRepaymentPlan result = new RepaymentService(repaymentMapper).getNextRepaymentPlan(10001L, "L10001");

        assertThat(result.totalAmount()).isEqualByComparingTo("1262.50");
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    void rejectsLoanOwnedByAnotherCustomer() {
        RepaymentPlanRow row = new RepaymentPlanRow(
                "L99999", 10002L, LocalDate.of(2026, 9, 18),
                new BigDecimal("800.00"), new BigDecimal("45.33"), BigDecimal.ZERO, "PENDING");
        RepaymentMapper repaymentMapper = loanNo -> row;

        assertThatThrownBy(() -> new RepaymentService(repaymentMapper)
                .getNextRepaymentPlan(10001L, "L99999"))
                .isInstanceOf(LoanAccessDeniedException.class);
    }
}
