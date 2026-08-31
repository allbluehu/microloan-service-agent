package com.example.microloan.settlement;

import com.example.microloan.repayment.LoanAccessDeniedException;
import com.example.microloan.security.AuthLevel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementService {

    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(8);

    private final SettlementMapper settlementMapper;

    public SettlementService(SettlementMapper settlementMapper) {
        this.settlementMapper = settlementMapper;
    }

    @Transactional(readOnly = true)
    public SettlementQuote calculate(Long authenticatedCustomerId, AuthLevel authLevel, String loanId,
                                     LocalDate settlementDate) {
        if (!authLevel.satisfies(AuthLevel.STEP_UP)) {
            throw new StepUpAuthRequiredException();
        }
        SettlementBasis basis = settlementMapper.findBasis(authenticatedCustomerId, loanId);
        if (basis == null) {
            throw new LoanAccessDeniedException();
        }
        if (settlementDate.isBefore(basis.interestAccruedThrough())) {
            throw new IllegalArgumentException("试算日期不能早于利息已计提日期");
        }

        long interestDays = ChronoUnit.DAYS.between(basis.interestAccruedThrough(), settlementDate);
        BigDecimal interest = basis.remainingPrincipal()
                .multiply(basis.annualRate())
                .multiply(BigDecimal.valueOf(interestDays))
                .divide(DAYS_PER_YEAR, 2, RoundingMode.HALF_UP);
        BigDecimal fee = new BigDecimal("0.00");
        BigDecimal total = basis.remainingPrincipal().add(interest).add(fee);
        // Quotes are read-only snapshots; the identifier exists for audit correlation, not payment execution.
        String quoteId = "Q-" + UUID.randomUUID();
        return new SettlementQuote(quoteId, basis.loanNo(), basis.remainingPrincipal(), interest, fee, total,
                settlementDate.atTime(LocalTime.MAX).atOffset(BUSINESS_OFFSET),
                "该结果仅为试算，不代表已经结清");
    }
}
