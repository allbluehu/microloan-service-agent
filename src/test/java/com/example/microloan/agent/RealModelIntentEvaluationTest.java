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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/**
 * GLM 真实意图分类质量门禁。
 * 调用链为“版本化样本 → 与生产一致的风险/复合意图前置过滤 → HTTP 调用 GLM → IntentClassifier 严格协议解析
 * → 聚合质量指标 → 先落盘报告再断言门槛”。使用显式开关，普通测试不会访问外部模型或消耗额度。
 */
@EnabledIfEnvironmentVariable(named = "REAL_MODEL_EVAL_ENABLED", matches = "true")
class RealModelIntentEvaluationTest {

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntentClassifier classifier = new IntentClassifier(
            new StaticListableBeanFactory().getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
            objectMapper, new ModelCallExecutor(Duration.ofSeconds(3)));
    private final RiskClassifier riskClassifier = new RiskClassifier();
    private final CompositeIntentDetector compositeIntentDetector = new CompositeIntentDetector();

    @Test
    void realModelMeetsVersionedIntentQualityGates() throws Exception {
        // 模型、端点、节流和质量门槛全部来自环境变量，使同一评测代码可复现不同部署配置。
        String apiKey = requiredEnvironment("GLM_API_KEY");
        String baseUrl = environment("GLM_BASE_URL", "https://open.bigmodel.cn/api/paas/v4");
        String path = environment("GLM_CHAT_COMPLETIONS_PATH", "/chat/completions");
        String model = environment("GLM_CHAT_MODEL", "glm-4.6");
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
        List<Long> latencies = new ArrayList<>();
        int evaluated = 0;
        int correct = 0;
        int protocolValid = 0;
        int rateLimited = 0;
        int providerErrors = 0;

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        URI endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", ""));
        for (JsonNode evaluationCase : cases) {
            String question = evaluationCase.path("question").asText();
            RiskLevel risk = riskClassifier.classify(question).level();
            if (risk == RiskLevel.HIGH || compositeIntentDetector.requiresClarification(question)) {
                // 高风险与复合意图在编排器中先于模型处理，不把模型未参与的样本计入意图质量。
                continue;
            }
            evaluated++;
            ObjectNode caseResult = results.addObject();
            caseResult.put("caseId", evaluationCase.path("id").asText());
            caseResult.put("expectedIntent", evaluationCase.path("expectedIntent").asText());
            long started = System.nanoTime();
            try {
                // 真实评测刻意直接记录每次供应商响应，不用业务规则掩盖 429、协议失败或传输故障。
                HttpResponse<String> response = invoke(client, endpoint, apiKey, model,
                        classifier.renderPrompt(question));
                long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
                latencies.add(latencyMs);
                caseResult.put("latencyMs", latencyMs);
                if (response.statusCode() == 429) {
                    rateLimited++;
                    caseResult.put("status", "RATE_LIMITED");
                } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    providerErrors++;
                    caseResult.put("status", "HTTP_ERROR");
                    caseResult.put("httpStatus", response.statusCode());
                } else {
                    String output = objectMapper.readTree(response.body())
                            .path("choices").path(0).path("message").path("content").asText();
                    IntentClassifier.ClassificationResult parsed = classifier.parseModelOutput(output);
                    if (parsed == null || parsed.confidence() < 0.75) {
                        caseResult.put("status", "PROTOCOL_REJECTED");
                    } else {
                        protocolValid++;
                        boolean matched = parsed.intent().name()
                                .equals(evaluationCase.path("expectedIntent").asText());
                        correct += matched ? 1 : 0;
                        caseResult.put("status", matched ? "CORRECT" : "INCORRECT");
                        caseResult.put("actualIntent", parsed.intent().name());
                        caseResult.put("confidence", parsed.confidence());
                    }
                }
            } catch (Exception exception) {
                providerErrors++;
                caseResult.put("status", "TRANSPORT_ERROR");
                // 报告只记录异常类型，避免响应或凭据意外进入评测产物。
                caseResult.put("exceptionType", exception.getClass().getSimpleName());
            }
            if (intervalMs > 0) {
                Thread.sleep(intervalMs);
            }
        }

        double accuracy = evaluated == 0 ? 0 : (double) correct / evaluated;
        double protocolRate = evaluated == 0 ? 0 : (double) protocolValid / evaluated;
        ObjectNode report = objectMapper.createObjectNode();
        // 报告绑定模型、提示词和数据集哈希，后续比较准确率时不会混淆输入版本。
        report.put("schemaVersion", "intent-evaluation-v1");
        report.put("generatedAt", Instant.now().toString());
        report.put("provider", "GLM");
        report.put("modelVersion", model);
        report.put("promptVersion", IntentClassifier.PROMPT_VERSION);
        report.put("dataset", "agent-routing-baseline.json");
        report.put("datasetSha256", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(datasetBytes)));
        report.put("evaluatedCases", evaluated);
        report.put("correctCases", correct);
        report.put("protocolValidCases", protocolValid);
        report.put("rateLimitedCases", rateLimited);
        report.put("providerErrorCases", providerErrors);
        report.put("accuracy", accuracy);
        report.put("protocolValidRate", protocolRate);
        report.put("p95LatencyMs", percentile95(latencies));
        report.put("accuracyGate", accuracyGate);
        report.put("protocolRateGate", protocolGate);
        report.set("cases", results);

        Path reportDirectory = Path.of("target", "evaluation");
        Files.createDirectories(reportDirectory);
        String safeModel = model.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path reportPath = reportDirectory.resolve(
                IntentClassifier.PROMPT_VERSION + "--" + safeModel + "--" + FILE_TIME.format(Instant.now()) + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        // 即使门槛失败报告也已写入，失败本身成为可分析、可追溯的评测结果。
        assertThat(accuracy)
                .as("真实模型准确率未达门槛，完整报告：%s", reportPath)
                .isGreaterThanOrEqualTo(accuracyGate);
        assertThat(protocolRate)
                .as("真实模型协议通过率未达门槛，完整报告：%s", reportPath)
                .isGreaterThanOrEqualTo(protocolGate);
    }

    private HttpResponse<String> invoke(HttpClient client, URI endpoint, String apiKey,
                                        String model, String prompt) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("temperature", 0);
        ArrayNode messages = payload.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(Long.parseLong(environment("REAL_MODEL_EVAL_TIMEOUT_SECONDS", "30"))))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload),
                        StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private long percentile95(List<Long> latencies) {
        if (latencies.isEmpty()) {
            return 0;
        }
        List<Long> sorted = latencies.stream().sorted(Comparator.naturalOrder()).toList();
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(index);
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
