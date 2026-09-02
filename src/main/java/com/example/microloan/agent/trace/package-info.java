/**
 * Agent 节点级追踪模块。
 *
 * <p>调用从 {@code AgentOrchestrator} 开始：每轮对话先创建 TraceContext，再按实际执行顺序写入风险、
 * 意图、路由和工具/知识节点。{@code AgentTraceService} 负责序号分配、内容脱敏和存储失败隔离，
 * {@code AgentTraceMapper} 负责持久化；查询则由 {@code AgentTraceController} 按当前客户归属读取。</p>
 *
 * <p>Trace 用于诊断和评测，不参与业务决策；写入失败不得覆盖已经成功的客户业务结果，记录中也不得
 * 保存客户原文、令牌、堆栈或供应商异常正文。</p>
 */
package com.example.microloan.agent.trace;
