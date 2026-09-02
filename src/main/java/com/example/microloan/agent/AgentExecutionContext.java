package com.example.microloan.agent;

/** 由服务端认证与请求追踪基础设施生成的可信执行上下文。 */
public record AgentExecutionContext(
        /** 当前认证客户的数据库主键，禁止从请求体或模型输出构造。 */
        Long customerId,
        /** 当前 HTTP 请求的全链路追踪标识。 */
        String traceId
) {}
