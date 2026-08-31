package com.example.microloan.agent;

import com.example.microloan.application.ApplicationStatus;
import com.example.microloan.repayment.NextRepaymentPlan;
import com.fasterxml.jackson.annotation.JsonValue;

public sealed interface ToolResult permits ToolResult.Application, ToolResult.Repayment {

    record Application(@JsonValue ApplicationStatus value) implements ToolResult {}

    record Repayment(@JsonValue NextRepaymentPlan value) implements ToolResult {}
}
