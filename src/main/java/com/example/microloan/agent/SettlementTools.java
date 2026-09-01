package com.example.microloan.agent;

import com.example.microloan.security.CustomerPrincipal;
import com.example.microloan.settlement.SettlementQuote;
import com.example.microloan.settlement.SettlementService;
import java.time.LocalDate;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SettlementTools {

    private final SettlementService settlementService;

    public SettlementTools(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Tool(description = "在二次认证后试算指定贷款于指定日期的提前结清金额，不执行真实支付")
    public SettlementQuote calculateEarlySettlement(String loanId, LocalDate settlementDate) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomerPrincipal customer)) {
            throw new IllegalStateException("Authenticated customer context is required");
        }
        // 二次认证等级来自签名令牌中的主体，工具只做试算且不触发真实扣款。
        return settlementService.calculate(customer.customerId(), customer.authLevel(), loanId, settlementDate);
    }
}
