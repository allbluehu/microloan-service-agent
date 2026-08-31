package com.example.microloan.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    @Test
    void capsCallerSuppliedLimitBeforeQuerying() {
        AtomicInteger appliedLimit = new AtomicInteger();
        PaymentMapper mapper = ownedLoanMapper(appliedLimit);

        new PaymentService(mapper).getPaymentRecords(10001L, "L10001", null, null, 5000);

        assertThat(appliedLimit).hasValue(PaymentService.MAX_RECORDS);
    }

    @Test
    void rejectsReversedTimeRangeBeforeReturningRecords() {
        PaymentMapper mapper = ownedLoanMapper(new AtomicInteger());
        OffsetDateTime from = OffsetDateTime.parse("2026-09-01T00:00:00+08:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-08-01T00:00:00+08:00");

        assertThatThrownBy(() -> new PaymentService(mapper)
                .getPaymentRecords(10001L, "L10001", from, to, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PaymentMapper ownedLoanMapper(AtomicInteger appliedLimit) {
        return new PaymentMapper() {
            @Override
            public int countOwnedLoan(Long customerId, String loanNo) {
                return 1;
            }

            @Override
            public List<PaymentRecord> findPayments(Long customerId, String loanNo, OffsetDateTime from,
                                                    OffsetDateTime to, int limit) {
                appliedLimit.set(limit);
                return List.of();
            }
        };
    }
}
