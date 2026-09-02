package com.example.microloan.agent;

import com.example.microloan.security.CustomerPrincipal;
import com.example.microloan.ticket.ServiceTicket;
import com.example.microloan.ticket.TicketPriority;
import com.example.microloan.ticket.TicketService;
import com.example.microloan.ticket.TicketType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TicketTools {

    private final TicketService ticketService;

    public TicketTools(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    public ServiceTicket createServiceTicket(String conversationId, TicketType type, String relatedLoanId,
                                             String summary, TicketPriority priority) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomerPrincipal customer)) {
            throw new IllegalStateException("Authenticated customer context is required");
        }
        // 客户身份与会话共同参与幂等键，重复编排只复用原工单，不重复创建人工任务。
        return ticketService.create(customer.customerId(), conversationId, type, relatedLoanId, summary, priority);
    }
}
