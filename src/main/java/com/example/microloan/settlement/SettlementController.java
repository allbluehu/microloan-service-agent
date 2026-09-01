package com.example.microloan.settlement;

import com.example.microloan.security.CustomerPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/{loanId}/settlement-quotes")
    public SettlementQuote calculate(@AuthenticationPrincipal CustomerPrincipal principal,
                                     @PathVariable String loanId,
                                     @Valid @RequestBody SettlementRequest request) {
        return settlementService.calculate(principal.customerId(), principal.authLevel(), loanId,
                request.settlementDate());
    }

    public record SettlementRequest(
            /** 客户期望办理提前结清的日期。 */
            @NotNull LocalDate settlementDate
    ) {}
}
