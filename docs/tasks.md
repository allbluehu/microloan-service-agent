# 任务与进度

> 会话接续入口：每次开工先读本文件了解进度，再看 `03-技术方案.md` 的 ADR 避免重提已否决方案。完成节点即时更新本文件；ADR 只追加不回改。

## 当前状态

阶段：迭代零（设计文档）完成，工程快修完成。迭代一（最小纵切）未开工。

## 已完成

- [x] 设计文档 7 份（README + 01~06）：ADR、用户故事、接口与表设计、测试与评测方案、迭代计划
- [x] 根 `.gitignore` 与首次 git 提交（设计文档入库，`.idea/` 不入库）
- [x] 技术选型钉版本：Spring Boot 3.4+、Spring AI 1.0.x GA 起步（初始化时核对兼容矩阵钉死小版本）
- [x] 流式选型决策：Spring MVC + SseEmitter 取代 WebFlux（ADR-005）
- [x] 幂等键公式统一：`customerId + conversationId + type + relatedResourceId`（03 与 04 对齐，requestId 仅作审计字段）
- [x] Resilience4j 边界澄清：超时、熔断、降级属 MVP（迭代四）；限流属增强功能
- [x] 迭代计划重排：迭代一改为纵切优先（第一周即打通业务 + AI 单场景闭环）
- [x] IDEA 本地配置修正：语言级别与字节码目标 JDK 1.8 → 21

## 下一步：迭代一开工清单

- [ ] IDEA Settings → Build Tools → Maven 改用 Bundled（当前指向 `~/File/apache-maven-3.3.9`，低于 Spring Boot 3 要求的 3.6.3+）
- [ ] 初始化 Maven 工程：Java 21 + Spring Boot 3.4+ 当前稳定版；核对 Spring AI 1.0.x 兼容矩阵后把两者小版本钉死写进 pom，模型 Base URL / API Key 走环境变量，仓库只放 `.env.example`
- [ ] 按 `06-实施计划与面试展示.md` 迭代一执行：customer/loan/repayment_plan 三表 + 种子数据、JWT 登录与资源归属校验、getNextRepaymentPlan 工具、Spring AI 意图分类、对话接口跑通单一场景

## 开放问题（到对应迭代再补，不阻塞开工）

- 意图分类的结构化输出 schema：JSON 结构、置信度字段、解析失败处理
- 路由伪代码缺 unknown / 低置信度意图兜底分支
- `AgentState.toolResult` 用 `Object` 承载，实现时改 sealed interface
- prompt 版本的存储 / 外部化方案：`agent_trace` 有 `prompt_version` 字段但无管理载体（加表或明确文件方案）
- `chat_session` / `chat_message` 表结构：ER 图有实体、`04` 核心表一节缺失
- RAG 待定项：rerank 手段（cross-encoder vs LLM 重排）、语义切分参数（chunk size / overlap）、embedding 模型与维度（DDL 中 `VECTOR(1024)` 仅为示意）
- 重试 / 超时参数量化：重试次数、退避策略、可重试错误类别（429 / 5xx / 超时）
- 复合意图处理策略（如"查账单，顺便投诉"），当前 `Intent` 为单值字段
