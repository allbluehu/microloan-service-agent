package com.example.microloan.agent;

import com.example.microloan.security.CustomerPrincipal;
import com.example.microloan.ticket.ServiceTicket;
import com.example.microloan.ticket.TicketPriority;
import com.example.microloan.ticket.TicketService;
import com.example.microloan.ticket.TicketType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
/**
 * Agent 风险/人工转接分支到工单领域服务的适配层。
 * 幂等键由会话、类型和关联资源构成，重复或并发执行由 TicketService 与数据库唯一约束收敛。
 */
public class TicketTools {

    private final TicketService ticketService;

    public TicketTools(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** 创建或返回既有服务工单；客户身份只能来自安全上下文。 */
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
