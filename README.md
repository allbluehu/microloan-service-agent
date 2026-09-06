# Microloan Service Agent

小额贷智能客服 Agent。当前已完成账单、申请进度、还款记录、提前结清试算、幂等工单、高风险转人工和知识问答链路：JWT 与短时二次认证、风险前置分类、确定性 Agent 路由、受控工具调用、资源归属校验、参数澄清、安全降级、节点级脱敏 Trace，以及带版本、有效期、受众和产品过滤的 PostgreSQL 全文＋pgvector 混合检索与可追溯引用。

## 本地验证

前置条件：Java 21、Maven 3.6.3+、Docker。

```bash
mvn test
```

测试通过 Testcontainers 自动启动 PostgreSQL，并执行 Flyway 迁移。首次运行需要拉取 `pgvector/pgvector:pg16` 和 Testcontainers Ryuk 镜像。
测试套件包含 50 条确定性 RAG 基线，用于约束证据召回、跨产品隔离和无答案拒答。
知识入库支持 Markdown 与文本型 PDF、SHA-256 校验和、语义切分、带重复版本保护的事务化导入，以及 `DRAFT → APPROVED → PUBLISHED → EXPIRED` 受控发布。PDF 导入校验文件签名、大小、页数、加密权限和提取结果；纯扫描件暂不支持 OCR。
仓库内置 10 份虚构 Markdown 知识资产。设置 `KNOWLEDGE_CLASSPATH_BOOTSTRAP_ENABLED=true` 可在启动时幂等导入为 `DRAFT`；导入不会自动审批或发布。

## 本地启动

```bash
docker compose up -d postgres
mvn spring-boot:run
```

启动后可打开 `http://localhost:8080/chat.html`，使用演示客户登录并进行可视化对话测试。

登录演示客户：

```bash
curl -s http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"customerNo":"C10001","password":"Demo123!"}'
```

取得 `accessToken` 后查询对话接口：

```bash
curl -s http://localhost:8080/api/v1/chat/sessions/S10001/messages \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <accessToken>' \
  -d '{"messageId":"M10001","content":"我下个月要还多少钱？","selectedLoanId":"L10001"}'
```

默认禁用真实模型，意图分类使用窄规则降级，知识问答只使用全文检索。项目通过 OpenAI 兼容协议解耦供应商，`.env.example` 默认给出百炼的 `qwen-plus` 与 `text-embedding-v4`：配置 `AI_API_KEY`，将 `AI_CHAT_MODEL` 设置为 `openai` 即可启用意图分类；需要向量检索时再将 `AI_EMBEDDING_MODEL` 设置为 `openai`，完成全部知识切片索引后启用 `KNOWLEDGE_VECTOR_ENABLED`。更多设计资料见 [文档中心](docs/README.md)。

真实 Embedding 与混合召回可通过 `REAL_EMBEDDING_EVAL_ENABLED=true mvn -Dtest=RealEmbeddingHybridEvaluationTest test` 在一次性 Testcontainers 数据库中验证，普通测试默认不会访问外部模型或消耗额度。

## 知识管理

知识治理 API 使用独立管理密钥，客户 JWT 无权导入或发布知识。本地 `.env` 至少配置：

```env
KNOWLEDGE_ADMIN_API_KEY=请替换为高强度随机值
```

调用时携带 `X-Knowledge-Admin-Key`。管理流程固定为“导入 DRAFT → 审批 APPROVED → 发布 PUBLISHED →
按需补建向量”；详细接口见 [接口与数据模型](docs/04-接口与数据模型.md)。向量入口要求
`AI_EMBEDDING_MODEL=openai`，返回的 `indexedChunkCount` 为本次实际补建数量，重复执行时只处理缺失或旧模型切片。

服务启动后可访问 `http://localhost:8080/knowledge-admin.html` 使用知识管理台。页面支持 Markdown/PDF
草稿导入、审批、发布、下线和增量向量索引；管理密钥只保存在当前浏览器标签页的 `sessionStorage` 中。

## 客户界面、坐席与自动化回归

`/chat.html` 现由 Vue 3 + TypeScript 提供历史会话、反馈、二次认证、业务卡片、引用、Trace 与 SSE 状态事件。`/support.html` 提供独立坐席密钥保护的工单处理、运行指标和本地评测报告查看。设置 `SUPPORT_API_KEY`（至少 32 字节）后使用坐席入口。前端构建产物随应用提供，演示启动不需要 Node。

完整启动、前端构建、接口、指标口径、断线重试和 8～10 分钟演示脚本见 [交付与演示验收](docs/09-交付与演示验收.md)。GitHub Actions 执行离线 Java 与浏览器回归；真实供应商评测仍需单独显式运行。
