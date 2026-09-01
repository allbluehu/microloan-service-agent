package com.example.microloan.ticket;

import java.time.OffsetDateTime;

public record ServiceTicket(
        String ticketId,
        String conversationId,
        String relatedLoanId,
        TicketType type,
        TicketPriority priority,
        String summary,
        String status,
        OffsetDateTime createdAt
) {}
