package com.example.microloan.agent;

import com.example.microloan.security.CustomerPrincipal;
import com.example.microloan.ticket.ServiceTicket;
import com.example.microloan.ticket.TicketPriority;
import com.example.microloan.ticket.TicketService;
import com.example.microloan.ticket.TicketType;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TicketTools {

    private final TicketService ticketService;

    public TicketTools(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Tool(description = "为已登录客户创建客服工单；相同会话、类型和关联资源重复调用会返回原工单")
    public ServiceTicket createServiceTicket(String conversationId, TicketType type, String relatedLoanId,
                                             String summary, TicketPriority priority) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomerPrincipal customer)) {
            throw new IllegalStateException("Authenticated customer context is required");
        }
        return ticketService.create(customer.customerId(), conversationId, type, relatedLoanId, summary, priority);
    }
}
