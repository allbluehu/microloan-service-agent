package com.example.microloan.agent;

import com.example.microloan.security.CustomerPrincipal;
import com.example.microloan.settlement.SettlementQuote;
import com.example.microloan.settlement.SettlementService;
import java.time.LocalDate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
/**
 * 编排器与提前结清试算服务之间的窄适配层。
 * 仅由 EARLY_SETTLEMENT_QUOTE 分支调用，二次认证、贷款归属和金额计算均由 SettlementService 控制。
 */
public class SettlementTools {

    private final SettlementService settlementService;

    public SettlementTools(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /** 按客户明确选择的贷款和日期生成实时试算，不缓存金融事实。 */
    public SettlementQuote calculateEarlySettlement(String loanId, LocalDate settlementDate) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomerPrincipal customer)) {
            throw new IllegalStateException("Authenticated customer context is required");
        }
        // 二次认证等级来自签名令牌中的主体，工具只做试算且不触发真实扣款。
        return settlementService.calculate(customer.customerId(), customer.authLevel(), loanId, settlementDate);
    }
}
