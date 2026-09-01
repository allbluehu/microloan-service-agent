package com.example.microloan.agent.trace;

import java.time.OffsetDateTime;

public record AgentTraceEntry(
        /** 节点在本轮执行链中的顺序号。 */
        int nodeSequence,
        /** Agent 节点名称。 */
        String nodeName,
        /** 已脱敏并截断的节点输入摘要。 */
        String inputSummary,
        /** 已脱敏并截断的节点输出摘要。 */
        String outputSummary,
        /** 节点实际使用的模型版本，无模型调用时为空。 */
        String modelVersion,
        /** 节点实际使用的提示词版本，无提示词时为空。 */
        String promptVersion,
        /** 节点执行耗时，单位为毫秒。 */
        long latencyMs,
        /** 节点执行状态。 */
        String status,
        /** Trace 记录创建时间。 */
        OffsetDateTime createdAt
) {}
