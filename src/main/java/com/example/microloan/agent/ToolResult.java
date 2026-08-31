package com.example.microloan.agent;

import com.example.microloan.application.ApplicationStatus;
import com.example.microloan.payment.PaymentHistory;
import com.example.microloan.repayment.NextRepaymentPlan;
import com.example.microloan.settlement.SettlementQuote;
import com.fasterxml.jackson.annotation.JsonValue;

public sealed interface ToolResult permits ToolResult.Application, ToolResult.Payment, ToolResult.Repayment,
        ToolResult.Settlement {

    record Application(@JsonValue ApplicationStatus value) implements ToolResult {}

    record Payment(@JsonValue PaymentHistory value) implements ToolResult {}

    record Repayment(@JsonValue NextRepaymentPlan value) implements ToolResult {}

    record Settlement(@JsonValue SettlementQuote value) implements ToolResult {}
}
