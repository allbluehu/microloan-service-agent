# Microloan Service Agent

小额贷智能客服 Agent。当前已完成“查询下一期账单”“查询贷款申请进度”“查询还款记录”和“提前结清试算”四条纵切：JWT 与短时二次认证、意图分类、确定性 Agent 路由、受控工具调用、资源归属校验和 PostgreSQL 集成测试。

## 本地验证

前置条件：Java 21、Maven 3.6.3+、Docker。

```bash
mvn test
```

测试通过 Testcontainers 自动启动 PostgreSQL，并执行 Flyway 迁移。首次运行需要拉取 `postgres:16-alpine` 和 Testcontainers Ryuk 镜像。

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

默认禁用真实模型，意图分类使用窄规则降级。接入 OpenAI 兼容服务时参考 `.env.example` 配置环境变量，并将 `AI_CHAT_MODEL` 设置为 `openai`。更多设计资料见 [文档中心](docs/README.md)。
