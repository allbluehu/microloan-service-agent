package com.example.microloan.agent.trace;

import com.example.microloan.agent.IntentClassifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentTraceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentTraceService.class);
    private final AgentTraceMapper traceMapper;
    private final String chatModelVersion;

    public AgentTraceService(AgentTraceMapper traceMapper,
                             @Value("${spring.ai.openai.chat.options.model:unconfigured}") String chatModelVersion) {
        this.traceMapper = traceMapper;
        this.chatModelVersion = chatModelVersion;
    }

    public TraceContext start(String traceId, String sessionId, Long customerId) {
        return new TraceContext(traceId, sessionId, customerId, new AtomicInteger());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(TraceContext context, String nodeName, String inputSummary, String outputSummary,
                        long latencyMs) {
        record(context, nodeName, inputSummary, outputSummary, null, null, latencyMs, "SUCCESS");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void intentSuccess(TraceContext context, String inputSummary, String outputSummary,
                              boolean modelUsed, long latencyMs) {
        // 只有模型结果真正通过协议与置信度门槛时才记录模型版本，规则降级不能伪装成模型成功。
        record(context, "INTENT_CLASSIFIER", inputSummary, outputSummary,
                modelUsed ? chatModelVersion : null, IntentClassifier.PROMPT_VERSION, latencyMs, "SUCCESS");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(TraceContext context, String nodeName, String inputSummary, RuntimeException exception,
                       long latencyMs) {
        // Exception type is enough for diagnosis here; messages may contain business or customer data.
        record(context, nodeName, inputSummary, "exception=" + exception.getClass().getSimpleName(),
                null, "INTENT_CLASSIFIER".equals(nodeName) ? IntentClassifier.PROMPT_VERSION : null,
                latencyMs, "FAILED");
    }

    public List<AgentTraceEntry> findOwnedTrace(Long customerId, String traceId) {
        return List.copyOf(traceMapper.findOwnedTrace(customerId, traceId));
    }

    private void record(TraceContext context, String nodeName, String inputSummary, String outputSummary,
                        String modelVersion, String promptVersion, long latencyMs, String status) {
        try {
            traceMapper.insert(context.traceId(), context.sessionId(), context.customerId(),
                    context.sequence().incrementAndGet(), nodeName, limit(inputSummary, 300), limit(outputSummary, 500),
                    modelVersion, promptVersion,
                    latencyMs, status);
        } catch (DataAccessException exception) {
            // Observability loss is reported locally but must not replace the customer-facing business outcome.
            LOGGER.warn("Failed to persist agent trace {} node {}", context.traceId(), nodeName);
        }
    }

    private String limit(String value, int maxLength) {
        String safe = value == null ? "" : value;
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    public record TraceContext(
            /** 本轮 Agent 执行的追踪标识。 */
            String traceId,
            /** 当前对话会话标识。 */
            String sessionId,
            /** Trace 所属客户主键。 */
            Long customerId,
            /** 用于生成单调递增节点顺序的计数器。 */
            AtomicInteger sequence
    ) {}
}
