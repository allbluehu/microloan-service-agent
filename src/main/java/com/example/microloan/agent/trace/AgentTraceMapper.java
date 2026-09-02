package com.example.microloan.agent.trace;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
/**
 * Agent Trace 持久化接口。
 * 写链由 AgentTraceService 统一脱敏后调用；读链在 SQL 中关联 customerId，避免 traceId 成为越权查询凭据。
 */
public interface AgentTraceMapper {

    @Insert("""
            INSERT INTO agent_trace
                (trace_id, session_id, customer_id, node_sequence, node_name, input_summary,
                 output_summary, model_version, prompt_version, latency_ms, status)
            VALUES
                (#{traceId}, #{sessionId}, #{customerId}, #{nodeSequence}, #{nodeName}, #{inputSummary},
                 #{outputSummary}, #{modelVersion}, #{promptVersion}, #{latencyMs}, #{status})
            """)
    void insert(@Param("traceId") String traceId,
                @Param("sessionId") String sessionId,
                @Param("customerId") Long customerId,
                @Param("nodeSequence") int nodeSequence,
                @Param("nodeName") String nodeName,
                @Param("inputSummary") String inputSummary,
                @Param("outputSummary") String outputSummary,
                @Param("modelVersion") String modelVersion,
                @Param("promptVersion") String promptVersion,
                @Param("latencyMs") long latencyMs,
                @Param("status") String status);

    @Select("""
            SELECT node_sequence, node_name, input_summary, output_summary, model_version,
                   prompt_version, latency_ms, status, created_at
            FROM agent_trace
            WHERE trace_id = #{traceId} AND customer_id = #{customerId}
            ORDER BY node_sequence
            """)
    List<AgentTraceEntry> findOwnedTrace(@Param("customerId") Long customerId,
                                         @Param("traceId") String traceId);
}
