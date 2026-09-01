package com.example.microloan.ticket;

import com.example.microloan.security.CustomerPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ServiceTicket create(@AuthenticationPrincipal CustomerPrincipal principal,
                                @Valid @RequestBody TicketRequest request) {
        return ticketService.create(principal.customerId(), request.conversationId(), request.type(),
                request.relatedLoanId(), request.summary(), request.priority());
    }

    public record TicketRequest(
            /** 发起工单的对话会话标识。 */
            @NotBlank String conversationId,
            /** 工单业务类型。 */
            @NotNull TicketType type,
            /** 关联贷款编号，可为空。 */
            String relatedLoanId,
            /** 客户问题摘要。 */
            @NotBlank String summary,
            /** 期望的处理优先级。 */
            @NotNull TicketPriority priority
    ) {}
}
