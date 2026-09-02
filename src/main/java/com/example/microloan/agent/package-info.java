/**
 * 面向客户的对话 Agent 编排模块。
 *
 * <h2>客户对话主调用链</h2>
 * <ol>
 *   <li>{@link com.example.microloan.agent.ChatController} 接收消息，只完成 HTTP 参数校验与响应转换。</li>
 *   <li>{@link com.example.microloan.agent.AgentOrchestrator#handle} 从安全上下文取得客户身份，调用
 *       {@link com.example.microloan.agent.ChatHistoryService} 固化消息和会话槽位。</li>
 *   <li>编排器依次执行 {@link com.example.microloan.agent.RiskClassifier}、
 *       {@link com.example.microloan.agent.CompositeIntentDetector} 和
 *       {@link com.example.microloan.agent.IntentClassifier}。高风险规则优先，复合业务请求要求澄清，
 *       单一普通请求才采用模型分类或规则降级结果。</li>
 *   <li>模型只返回受控的 {@link com.example.microloan.agent.Intent}，编排器通过固定 switch 白名单调用
 *       RepaymentTools、ApplicationTools、PaymentTools、SettlementTools、TicketTools 或知识服务；
 *       模型不能直接执行方法，也不能提供 customerId。</li>
 *   <li>业务结果包装为 {@link com.example.microloan.agent.ToolResult} 并写回不可变
 *       {@link com.example.microloan.agent.AgentState}，随后保存助手消息并返回 API。</li>
 * </ol>
 *
 * <h2>横切约束</h2>
 * <p>{@code agent.trace} 在风险、意图、路由和执行节点记录脱敏摘要；模型调用由
 * {@link com.example.microloan.agent.ModelCallExecutor} 统一实施超时、并发隔离和熔断降级。
 * 客户资源归属仍由各业务 Service/SQL 校验，不能把意图识别结果当作授权依据。</p>
 */
package com.example.microloan.agent;
