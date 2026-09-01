# 任务与进度

> 会话接续入口：每次开工先读本文件了解进度，再看 `03-技术方案.md` 的 ADR 避免重提已否决方案。完成节点即时更新本文件；ADR 只追加不回改。

## 当前状态

阶段：迭代一、二本地工程闭环完成；迭代三进行中，已完成带发布状态、有效期、受众、产品和地区约束的全文知识检索、引用与低召回拒答。向量检索和真实模型联调等待可用凭据及 Embedding 维度决策。

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
- [x] 实现短时 STEP_UP JWT、提前结清试算 API 与 calculateEarlySettlement 工具
- [x] 金额由 Java 按剩余本金、年利率和计息天数计算，普通登录及跨客户访问均被拒绝（当前共 22 个测试）
- [x] 建立 service_ticket 表、数据库唯一幂等键与 createServiceTicket 工具
- [x] 高风险投诉先于普通意图路由，自动创建高优先级工单；明确要求人工直接转接
- [x] 验证重复请求和并发事务只返回一个工单（当前共 28 个测试）
- [x] 建立 agent_trace 表，记录风险、意图、路由、工具节点的顺序、耗时和结构化结果
- [x] Trace 不保存用户原文或异常消息，并按当前客户隔离查询权限
- [x] 缺少贷款、申请或试算日期时先澄清，不携带空参数调用工具
- [x] 未登录、无效 JWT、二次认证失败使用稳定错误码；未知工具异常降级为 TOOL_UNAVAILABLE
- [x] Trace 存储故障不覆盖正常业务结果（当前共 31 个测试）
- [x] 建立版本化 knowledge_document / knowledge_chunk 表与 PostgreSQL 全文检索索引
- [x] 知识召回强制过滤发布状态、有效期、受众、产品和地区，并返回文档与切片引用
- [x] 无合格证据时拒绝生成答案，覆盖过期、内部及跨产品知识隔离（当前共 35 个测试）

## 下一步

- [x] 确认命令行 Maven 3.9.9，满足 Spring Boot 3.5.16 对 Maven 3.6.3+ 的要求；IDEA 可按个人习惯选择 Bundled 或该版本
- [x] 初始化 Maven 工程：Java 21 + Spring Boot 3.5.16 + Spring AI 1.0.9；模型配置走环境变量，仓库只放 `.env.example`
- [x] 打通迭代一纵切：三表 + 种子数据、JWT 与归属校验、getNextRepaymentPlan 工具、意图分类、对话接口和 Testcontainers 测试
- [ ] 配置可用模型凭据，验证真实模型意图分类；未配置或模型失败时保持规则降级
- [x] 将演示数据密码替换为 BCrypt 哈希，并覆盖错误密码、未知客户和未认证访问测试
- [x] 建立 ticket 表与业务模块
- [x] 实现幂等工单工具链
- [x] 完善认证不足、歧义和工具失败路线
- [x] 保存节点级 Agent Trace
- [x] 打通知识问答最小纵切：意图路由、全文召回、元数据过滤、引用和低召回拒答
- [ ] 确定 Embedding 模型与维度，引入 pgvector，并实现全文与向量混合召回
- [ ] 建立首批 RAG 评测集，验证 Recall@K、知识隔离和拒答准确性

## 开放问题（到对应迭代再补，不阻塞开工）

- 意图分类的结构化输出 schema：JSON 结构、置信度字段、解析失败处理
- 路由伪代码缺 unknown / 低置信度意图兜底分支
- [x] `AgentState.toolResult` 已使用 sealed interface，具体业务 DTO 通过同包 wrapper 封装
- prompt 版本的存储 / 外部化方案：`agent_trace` 有 `prompt_version` 字段但无管理载体（加表或明确文件方案）
- `chat_session` / `chat_message` 表结构：ER 图有实体、`04` 核心表一节缺失
- RAG 待定项：rerank 手段（cross-encoder vs LLM 重排）、语义切分参数（chunk size / overlap）、Embedding 模型与维度；当前迁移仅落全文索引，不写入未经验证的向量维度
- 重试 / 超时参数量化：重试次数、退避策略、可重试错误类别（429 / 5xx / 超时）
- 复合意图处理策略（如"查账单，顺便投诉"），当前 `Intent` 为单值字段
