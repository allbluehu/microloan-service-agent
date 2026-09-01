package com.example.microloan.agent;

import com.example.microloan.ticket.TicketType;

public record RiskAssessment(RiskLevel level, TicketType ticketType) {

    public static RiskAssessment low() {
        return new RiskAssessment(RiskLevel.LOW, null);
    }
}
