package com.example.microloan.payment;

import com.example.microloan.repayment.LoanAccessDeniedException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    public static final int MAX_RECORDS = 50;

    private final PaymentMapper paymentMapper;

    public PaymentService(PaymentMapper paymentMapper) {
        this.paymentMapper = paymentMapper;
    }

    @Transactional(readOnly = true)
    public PaymentHistory getPaymentRecords(Long authenticatedCustomerId, String loanId,
                                            OffsetDateTime from, OffsetDateTime to, Integer requestedLimit) {
        if (paymentMapper.countOwnedLoan(authenticatedCustomerId, loanId) == 0) {
            throw new LoanAccessDeniedException();
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("查询开始时间必须早于结束时间");
        }
        // Bound results before they enter the model context, regardless of caller-supplied limits.
        int limit = requestedLimit == null ? 20 : Math.max(1, Math.min(requestedLimit, MAX_RECORDS));
        List<PaymentRecord> records = paymentMapper.findPayments(authenticatedCustomerId, loanId, from, to, limit);
        return new PaymentHistory(loanId, List.copyOf(records));
    }
}
