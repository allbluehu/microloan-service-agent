package com.example.microloan.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.microloan.security.AuthLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SettlementServiceTest {

    private final SettlementMapper mapper = (customerId, loanNo) -> new SettlementBasis(
            loanNo, new BigDecimal("8200.00"), new BigDecimal("0.075000"), LocalDate.of(2026, 8, 14));

    @Test
    void calculatesInterestAndTotalInJava() {
        SettlementQuote quote = new SettlementService(mapper).calculate(
                10001L, AuthLevel.STEP_UP, "L10001", LocalDate.of(2026, 9, 3));

        assertThat(quote.interest()).isEqualByComparingTo("33.70");
        assertThat(quote.totalAmount()).isEqualByComparingTo("8233.70");
    }

    @Test
    void rejectsLoginLevelBeforeReadingLoanData() {
        assertThatThrownBy(() -> new SettlementService(mapper).calculate(
                10001L, AuthLevel.LOGIN, "L10001", LocalDate.of(2026, 9, 3)))
                .isInstanceOf(StepUpAuthRequiredException.class);
    }
}
