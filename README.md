# Microloan Service Agent

小额贷智能客服 Agent。当前已完成账单、申请进度、还款记录、提前结清试算、幂等工单、高风险转人工和知识问答链路：JWT 与短时二次认证、风险前置分类、确定性 Agent 路由、受控工具调用、资源归属校验、参数澄清、安全降级、节点级脱敏 Trace，以及带版本、有效期、受众和产品过滤的 PostgreSQL 全文＋pgvector 混合检索与可追溯引用。

## 本地验证

前置条件：Java 21、Maven 3.6.3+、Docker。

```bash
mvn test
```

测试通过 Testcontainers 自动启动 PostgreSQL，并执行 Flyway 迁移。首次运行需要拉取 `pgvector/pgvector:pg16` 和 Testcontainers Ryuk 镜像。
测试套件包含 50 条确定性 RAG 基线，用于约束证据召回、跨产品隔离和无答案拒答。
知识入库核心支持带元数据头的 Markdown 解析、SHA-256 校验和，以及按标题层级和段落边界切分；数据库草稿导入与 PDF 解析仍在迭代中。

## 本地启动

```bash
docker compose up -d postgres
mvn spring-boot:run
```

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

默认禁用真实模型，意图分类使用窄规则降级，知识问答只使用全文检索。接入 OpenAI 兼容服务时参考 `.env.example`：将 `AI_CHAT_MODEL` 和 `AI_EMBEDDING_MODEL` 设置为 `openai`，完成向量索引后启用 `KNOWLEDGE_VECTOR_ENABLED`。更多设计资料见 [文档中心](docs/README.md)。
