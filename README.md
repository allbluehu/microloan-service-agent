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

默认禁用真实模型，意图分类使用窄规则降级，知识问答只使用全文检索。项目选用 GLM 的 OpenAI 兼容接口：参考 `.env.example` 配置 `GLM_API_KEY`，将 `AI_CHAT_MODEL` 设置为 `openai` 即可启用意图分类；需要向量检索时再将 `AI_EMBEDDING_MODEL` 设置为 `openai`，使用 `embedding-3` 完成全部知识切片索引后启用 `KNOWLEDGE_VECTOR_ENABLED`。更多设计资料见 [文档中心](docs/README.md)。
