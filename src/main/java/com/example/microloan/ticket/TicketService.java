package com.example.microloan.ticket;

import com.example.microloan.repayment.LoanAccessDeniedException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final TicketMapper ticketMapper;

    public TicketService(TicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
    }

    @Transactional
    public ServiceTicket create(Long customerId, String conversationId, TicketType type, String relatedLoanId,
                                String summary, TicketPriority priority) {
        if (relatedLoanId != null && ticketMapper.countOwnedLoan(customerId, relatedLoanId) == 0) {
            throw new LoanAccessDeniedException();
        }
        String normalizedResource = relatedLoanId == null ? "NONE" : relatedLoanId;
        String idempotencyKey = customerId + ":" + conversationId + ":" + type + ":" + normalizedResource;
        String safeSummary = normalizeSummary(summary);

        // The unique key is the final concurrency guard; ON CONFLICT makes retries return the first ticket.
        ticketMapper.insertIfAbsent("T-" + UUID.randomUUID(), customerId, conversationId, relatedLoanId,
                type.name(), priority.name(), safeSummary, idempotencyKey);
        ServiceTicket ticket = ticketMapper.findByIdempotencyKey(idempotencyKey);
        if (ticket == null) {
            throw new IllegalStateException("Ticket idempotency lookup failed");
        }
        return ticket;
    }

    private String normalizeSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("工单摘要不能为空");
        }
        String normalized = summary.strip().replaceAll("\\s+", " ");
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
