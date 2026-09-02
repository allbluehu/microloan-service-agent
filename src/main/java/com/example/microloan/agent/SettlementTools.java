package com.example.microloan.agent;

import com.example.microloan.security.CustomerPrincipal;
import com.example.microloan.settlement.SettlementQuote;
import com.example.microloan.settlement.SettlementService;
import java.time.LocalDate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SettlementTools {

    private final SettlementService settlementService;

    public SettlementTools(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    public SettlementQuote calculateEarlySettlement(String loanId, LocalDate settlementDate) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomerPrincipal customer)) {
            throw new IllegalStateException("Authenticated customer context is required");
        }
        // 二次认证等级来自签名令牌中的主体，工具只做试算且不触发真实扣款。
        return settlementService.calculate(customer.customerId(), customer.authLevel(), loanId, settlementDate);
    }
}
