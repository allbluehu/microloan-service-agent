# 任务与进度

> 会话接续入口：每次开工先读本文件了解进度，再看 `03-技术方案.md` 的 ADR 避免重提已否决方案。完成节点即时更新本文件；ADR 只追加不回改。

## 当前状态

阶段：迭代一（最小纵切）本地工程闭环完成；迭代二进行中，申请进度、还款记录工具链与 Agent 确定性路由已完成。真实模型联调等待可用凭据。

## 投入纪律

- 必须完成线：v0.3（纵切 + Agent 状态机 + RAG）；v0.4/v1.0 是加分项，不是承诺。
- 止损规则：迭代一纵切两周内未跑通闭环，降级为仅 RAG 问答项目，不让项目烂在半路。
- 砍减顺序：Vue 前端 → 评测看板 → 长期会话增强；安全测试与评测集不砍（差异化所在）。

## 已完成

- [x] 设计文档 7 份（README + 01~06）：ADR、用户故事、接口与表设计、测试与评测方案、迭代计划
- [x] 根 `.gitignore` 与首次 git 提交（设计文档入库，`.idea/` 不入库）
- [x] 技术选型钉版本：Spring Boot 3.4+、Spring AI 1.0.x GA 起步（初始化时核对兼容矩阵钉死小版本）
- [x] 流式选型决策：Spring MVC + SseEmitter 取代 WebFlux（ADR-005）
- [x] 幂等键公式统一：`customerId + conversationId + type + relatedResourceId`（03 与 04 对齐，requestId 仅作审计字段）
- [x] Resilience4j 边界澄清：超时、熔断、降级属 MVP（迭代四）；限流属增强功能
- [x] 迭代计划重排：迭代一改为纵切优先（第一周即打通业务 + AI 单场景闭环）
- [x] IDEA 本地配置修正：语言级别与字节码目标 JDK 1.8 → 21
- [x] 初始化 Maven 工程并钉死 Spring Boot 3.5.16、Spring AI 1.0.9、Java 21
- [x] 建立 customer、loan、repayment_plan 三表、Flyway 迁移与虚构种子数据
- [x] 实现 JWT 登录、loan 资源归属校验和下一期账单业务 API
- [x] 实现 Spring AI 意图分类入口、规则降级、账单工具与非流式对话接口
- [x] 使用 Testcontainers 验证登录、账单对话闭环及 API/Agent 两层横向越权拦截（当前共 8 个测试）
- [x] 统一 MVC 与 Security Filter 的 `code/message/traceId` 错误响应契约
- [x] 建立 loan_application 表、申请进度 API 与 getApplicationStatus 工具
- [x] 从单场景 Controller 抽出 AgentOrchestrator，并引入 AgentState 与 sealed ToolResult
- [x] 验证申请进度对话及 API/Agent 两层横向越权拦截（当前共 11 个测试）
- [x] 建立 payment_record 表、还款记录 API 与 getPaymentRecords 工具
- [x] 在数据库和服务层限制查询范围，业务 DTO 仅暴露银行卡尾号（当前共 17 个测试）

## 下一步

- [x] 确认命令行 Maven 3.9.9，满足 Spring Boot 3.5.16 对 Maven 3.6.3+ 的要求；IDEA 可按个人习惯选择 Bundled 或该版本
- [x] 初始化 Maven 工程：Java 21 + Spring Boot 3.5.16 + Spring AI 1.0.9；模型配置走环境变量，仓库只放 `.env.example`
- [x] 打通迭代一纵切：三表 + 种子数据、JWT 与归属校验、getNextRepaymentPlan 工具、意图分类、对话接口和 Testcontainers 测试
- [ ] 配置可用模型凭据，验证真实模型意图分类；未配置或模型失败时保持规则降级
- [x] 将演示数据密码替换为 BCrypt 哈希，并覆盖错误密码、未知客户和未认证访问测试
- [ ] 建立 ticket 表与业务模块
- [ ] 实现提前结清试算和幂等工单工具链
- [ ] 增加风险分类、认证不足、歧义和工具失败路线
- [ ] 保存节点级 Agent Trace

## 开放问题（到对应迭代再补，不阻塞开工）

- 意图分类的结构化输出 schema：JSON 结构、置信度字段、解析失败处理
- 路由伪代码缺 unknown / 低置信度意图兜底分支
- [x] `AgentState.toolResult` 已使用 sealed interface，具体业务 DTO 通过同包 wrapper 封装
- prompt 版本的存储 / 外部化方案：`agent_trace` 有 `prompt_version` 字段但无管理载体（加表或明确文件方案）
- `chat_session` / `chat_message` 表结构：ER 图有实体、`04` 核心表一节缺失
- RAG 待定项：rerank 手段（cross-encoder vs LLM 重排）、语义切分参数（chunk size / overlap）、embedding 模型与维度（DDL 中 `VECTOR(1024)` 仅为示意）
- 重试 / 超时参数量化：重试次数、退避策略、可重试错误类别（429 / 5xx / 超时）
- 复合意图处理策略（如"查账单，顺便投诉"），当前 `Intent` 为单值字段
