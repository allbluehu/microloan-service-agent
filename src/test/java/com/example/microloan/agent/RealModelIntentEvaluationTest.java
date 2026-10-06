package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/**
 * OpenAI 兼容供应商真实意图分类质量门禁。
 * 调用链为“版本化样本 → 本地风险规则/复合意图前置过滤 → HTTP 调用供应商 → IntentClassifier 严格协议解析
 * → 保存全部合法预测 → 离线阈值扫描 → 先落盘报告再断言门槛”。使用显式开关，普通测试不会访问外部模型或消耗额度。
 */
@EnabledIfEnvironmentVariable(named = "REAL_MODEL_EVAL_ENABLED", matches = "true")
class RealModelIntentEvaluationTest {

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntentClassifier classifier = new IntentClassifier(
            new StaticListableBeanFactory().getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
            objectMapper, new ModelCallExecutor(Duration.ofSeconds(3)));
    private final RiskClassifier riskClassifier = new RiskClassifier(
            new RuleRiskClassifier(), new RiskDecisionPolicy(0.75));
    private final CompositeIntentDetector compositeIntentDetector = new CompositeIntentDetector();

    @Test
    void realModelMeetsVersionedIntentQualityGates() throws Exception {
        // 模型、端点、节流和质量门槛全部来自环境变量，使同一评测代码可复现不同部署配置。
        String provider = environment("AI_PROVIDER", "BAILIAN");
        String apiKey = requiredEnvironment("AI_API_KEY");
        String baseUrl = environment("AI_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        String path = environment("AI_CHAT_COMPLETIONS_PATH", "/chat/completions");
        String model = environment("AI_CHAT_MODEL_NAME", "qwen-plus");
        long intervalMs = Long.parseLong(environment("REAL_MODEL_EVAL_INTERVAL_MS", "500"));
        double accuracyGate = Double.parseDouble(environment("REAL_MODEL_EVAL_MIN_ACCURACY", "0.90"));
        double protocolGate = Double.parseDouble(environment("REAL_MODEL_EVAL_MIN_PROTOCOL_RATE", "0.95"));

        byte[] datasetBytes;
        try (InputStream input = getClass().getResourceAsStream("/evaluation/agent-routing-baseline.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing agent routing evaluation dataset");
            }
            datasetBytes = input.readAllBytes();
        }
        JsonNode cases = objectMapper.readTree(datasetBytes);
        ArrayNode results = objectMapper.createArrayNode();
        double acceptanceThreshold = Double.parseDouble(environment("REAL_MODEL_EVAL_THRESHOLD", "0.75"));
        List<Double> thresholds = new ArrayList<>(IntentEvaluationReport.thresholds(
                environment("REAL_MODEL_EVAL_THRESHOLDS", "0,0.5,0.7,0.75,0.8,0.9,0.95,0.98,0.99,1")));
        thresholds.add(acceptanceThreshold);
        long timeoutSeconds = Long.parseLong(environment("REAL_MODEL_EVAL_TIMEOUT_SECONDS", "30"));
        if (intervalMs < 0 || timeoutSeconds <= 0 || thresholds.stream().anyMatch(
                t -> !Double.isFinite(t) || t < 0 || t > 1)
                || !Double.isFinite(accuracyGate) || accuracyGate < 0 || accuracyGate > 1
                || !Double.isFinite(protocolGate) || protocolGate < 0 || protocolGate > 1) {
            throw new IllegalArgumentException("评测时间和阈值配置不合法");
        }
        // 开始消耗模型额度前检查样本身份，拒绝空标识、重复标识和非法预期标签。
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (JsonNode evaluationCase : cases) {
            String id = evaluationCase.path("id").asText();
            if (id.isBlank() || !ids.add(id) || evaluationCase.path("question").asText().isBlank()) {
                throw new IllegalArgumentException("评测样本必须包含唯一非空 id 和 question");
            }
            boolean routedAway = riskClassifier.classify(evaluationCase.path("question").asText()).level() == RiskLevel.HIGH
                    || compositeIntentDetector.requiresClarification(evaluationCase.path("question").asText());
            // 高风险和复合意图样本原本没有普通意图标签；只有实际进入模型的样本才要求该标签。
            if (!routedAway) Intent.valueOf(evaluationCase.path("expectedIntent").asText());
        }
        int evaluated = 0;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        URI endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", ""));
        for (JsonNode evaluationCase : cases) {
            String question = evaluationCase.path("question").asText();
            ObjectNode caseResult = results.addObject();
            caseResult.put("caseId", evaluationCase.path("id").asText());
            caseResult.put("expectedIntent", evaluationCase.path("expectedIntent").asText());
            RiskLevel risk = riskClassifier.classify(question).level();
            if (risk == RiskLevel.HIGH || compositeIntentDetector.requiresClarification(question)) {
                // 前置接管样本留在报告中，但不计入模型调用分母；便于核对过滤范围是否发生变化。
                caseResult.put("status", risk == RiskLevel.HIGH ? "SKIPPED_HIGH_RISK" : "SKIPPED_COMPOSITE");
                continue;
            }
            evaluated++;
            // 本测试分类器没有 ChatModel，仅调用现有确定性规则，供离线模拟未采纳时的标签回退。
            caseResult.put("ruleIntent", classifier.classify(question).name());
            String prompt = classifier.renderPrompt(question);
            long started = System.nanoTime();
            try {
                // 直接 HTTP 采集不走线上超时/重试/熔断，不用规则覆盖供应商的真实失败记录。
                HttpResponse<String> response = invoke(client, endpoint, apiKey, model, prompt, timeoutSeconds);
                IntentEvaluationReport.recordResponse(caseResult, response.statusCode(), response.body(), classifier, objectMapper);
            } catch (java.net.http.HttpTimeoutException exception) {
                caseResult.put("status", "TIMEOUT");
                caseResult.put("exceptionType", exception.getClass().getSimpleName());
            } catch (java.io.IOException exception) {
                caseResult.put("status", "TRANSPORT_ERROR");
                caseResult.put("exceptionType", exception.getClass().getSimpleName());
            } finally {
                // 所有已开始尝试均保存耗时，包含超时和网络失败；不将间隔节流时间计入请求延迟。
                caseResult.put("latencyMs", Duration.ofNanos(System.nanoTime() - started).toMillis());
            }
            if (intervalMs > 0) Thread.sleep(intervalMs);
        }

        ObjectNode report = objectMapper.createObjectNode();
        report.put("schemaVersion", "intent-evaluation-v2");
        report.put("generatedAt", Instant.now().toString());
        report.put("provider", provider);
        report.put("modelVersion", model);
        report.put("promptVersion", IntentClassifier.PROMPT_VERSION);
        report.put("dataset", "agent-routing-baseline.json");
        report.put("datasetSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datasetBytes)));
        report.put("evaluatedCases", evaluated);
        report.put("totalCases", cases.size());
        report.put("acceptanceThreshold", acceptanceThreshold);
        report.put("requestCorrectRateGate", accuracyGate);
        report.put("protocolSuccessRateGate", protocolGate);
        report.put("timeoutSeconds", timeoutSeconds);
        report.put("connectTimeoutSeconds", 10);
        report.put("intervalMs", intervalMs);
        report.put("maxAttempts", 1);
        report.put("temperature", 0);
        report.put("executionMode", "DIRECT_HTTP_NO_PRODUCTION_RETRY_OR_CIRCUIT_BREAKER");
        report.put("codeVersion", environment("REAL_MODEL_EVAL_CODE_VERSION", "unrecorded"));
        report.set("cases", results);
        ObjectNode analysis = IntentEvaluationReport.analyze(report, thresholds, objectMapper);
        report.set("analysis", analysis);

        Path reportDirectory = Path.of("target", "evaluation");
        Files.createDirectories(reportDirectory);
        String safeModel = model.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path reportPath = reportDirectory.resolve(
                IntentClassifier.PROMPT_VERSION + "--" + safeModel + "--" + FILE_TIME.format(Instant.now()) + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        // 先落盘全部预测及阈值扫描，再检查指定阈值；测试不自动挑选并宣称“最优阈值”。
        JsonNode selected = java.util.stream.StreamSupport.stream(analysis.path("thresholds").spliterator(), false)
                .filter(point -> Double.compare(point.path("threshold").asDouble(), acceptanceThreshold) == 0)
                .findFirst().orElseThrow();
        assertThat(evaluated).as("没有实际评测样本，报告：%s", reportPath).isPositive();
        assertThat(selected.path("requestCorrectRate").asDouble())
                .as("阈值下正确采纳占全部请求的比例未达门槛，报告：%s", reportPath)
                .isGreaterThanOrEqualTo(accuracyGate);
        assertThat(analysis.path("protocolSuccessRate").asDouble())
                .as("协议合法结果占全部请求的比例未达门槛，报告：%s", reportPath)
                .isGreaterThanOrEqualTo(protocolGate);
    }

    private HttpResponse<String> invoke(HttpClient client, URI endpoint, String apiKey,
                                        String model, String prompt, long timeoutSeconds) throws java.io.IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("temperature", 0);
        payload.putArray("messages").addObject().put("role", "user").put("content", prompt);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
