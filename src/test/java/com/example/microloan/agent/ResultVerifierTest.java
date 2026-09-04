package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.microloan.repayment.NextRepaymentPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 验证结果节点阻断资源错配和金额分项不一致，合法权威结果保持原状态返回。 */
class ResultVerifierTest {

    private final ResultVerifier verifier = new ResultVerifier();

    @Test
    void acceptsConsistentRepaymentResult() {
        AgentState state = repaymentState("L10001", "12.00", "10.00", "1.00", "1.00");
        assertThat(verifier.verify(state)).isSameAs(state);
    }

    @Test
    void rejectsResourceMismatchAndIncorrectTotal() {
        assertThatThrownBy(() -> verifier.verify(
                repaymentState("L99999", "12.00", "10.00", "1.00", "1.00")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("resource");
        assertThatThrownBy(() -> verifier.verify(
                repaymentState("L10001", "13.00", "10.00", "1.00", "1.00")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("components");
    }

    private AgentState repaymentState(String actualLoanId, String total, String principal,
                                      String interest, String fee) {
        NextRepaymentPlan plan = new NextRepaymentPlan(actualLoanId, LocalDate.of(2026, 9, 15),
                new BigDecimal(principal), new BigDecimal(interest), new BigDecimal(fee),
                new BigDecimal(total), "PENDING");
        return new AgentState("trace", "session", "message", "content", "L10001", null, null, "ALL",
                null, RiskLevel.LOW, null, Intent.NEXT_REPAYMENT_QUERY,
                new ToolResult.Repayment(plan), "answer");
    }
}
