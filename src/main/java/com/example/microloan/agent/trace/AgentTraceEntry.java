package com.example.microloan.agent.trace;

import java.time.OffsetDateTime;

public record AgentTraceEntry(
        int nodeSequence,
        String nodeName,
        String inputSummary,
        String outputSummary,
        String modelVersion,
        String promptVersion,
        long latencyMs,
        String status,
        OffsetDateTime createdAt
) {}
