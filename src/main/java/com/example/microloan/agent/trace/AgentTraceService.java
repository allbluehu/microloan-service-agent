package com.example.microloan.agent.trace;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class AgentTraceService {

    private static final String PROMPT_VERSION = "intent-v1";

    private final AgentTraceMapper traceMapper;

    public AgentTraceService(AgentTraceMapper traceMapper) {
        this.traceMapper = traceMapper;
    }

    public TraceContext start(String traceId, String sessionId, Long customerId) {
        return new TraceContext(traceId, sessionId, customerId, new AtomicInteger());
    }

    public void success(TraceContext context, String nodeName, String inputSummary, String outputSummary,
                        long latencyMs) {
        record(context, nodeName, inputSummary, outputSummary, latencyMs, "SUCCESS");
    }

    public void failed(TraceContext context, String nodeName, String inputSummary, RuntimeException exception,
                       long latencyMs) {
        // Exception type is enough for diagnosis here; messages may contain business or customer data.
        record(context, nodeName, inputSummary, "exception=" + exception.getClass().getSimpleName(),
                latencyMs, "FAILED");
    }

    public List<AgentTraceEntry> findOwnedTrace(Long customerId, String traceId) {
        return List.copyOf(traceMapper.findOwnedTrace(customerId, traceId));
    }

    private void record(TraceContext context, String nodeName, String inputSummary, String outputSummary,
                        long latencyMs, String status) {
        traceMapper.insert(context.traceId(), context.sessionId(), context.customerId(),
                context.sequence().incrementAndGet(), nodeName, limit(inputSummary, 300), limit(outputSummary, 500),
                null, "INTENT_CLASSIFIER".equals(nodeName) ? PROMPT_VERSION : null, latencyMs, status);
    }

    private String limit(String value, int maxLength) {
        String safe = value == null ? "" : value;
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    public record TraceContext(String traceId, String sessionId, Long customerId, AtomicInteger sequence) {}
}
