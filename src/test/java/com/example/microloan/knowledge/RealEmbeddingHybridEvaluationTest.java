package com.example.microloan.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * OpenAI 兼容供应商真实向量与混合召回端到端评测。
 * 调用链覆盖“Flyway 建库 → classpath 文档导入草稿 → 审批/发布 → Embedding 建索引 →
 * PostgreSQL 全文与 pgvector 双路召回 → RRF → 确定性重排 → KnowledgeService 引用”，
 * 并使用一次性 Testcontainers 数据库隔离开发数据。必须显式开启，避免常规测试访问外部服务和消耗额度。
 */
@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=openai",
        "knowledge.ingestion.classpath-bootstrap-enabled=true",
        "knowledge.vector.indexing-enabled=false",
        "knowledge.vector.enabled=true"
})
@Testcontainers
@EnabledIfEnvironmentVariable(named = "REAL_EMBEDDING_EVAL_ENABLED", matches = "true")
class RealEmbeddingHybridEvaluationTest {

    private static final String MODEL = environment("AI_EMBEDDING_MODEL_NAME", "text-embedding-v4");
    private static final String PROVIDER = environment("AI_PROVIDER", "BAILIAN");
    private static final int EXPECTED_DIMENSIONS = 1024;
    private static final List<String> DOCUMENTS = List.of(
            "KB-MB-MATERIALS", "KB-MB-ELIGIBILITY", "KB-MB-PROCESS", "KB-MB-TERM",
            "KB-GENERAL-REPAYMENT", "KB-GENERAL-SETTLEMENT", "KB-PC-MATERIALS", "KB-PC-PROCESS",
            "KB-SERVICE-PAYMENT-NOT-POSTED", "KB-SERVICE-ACCOUNT-SECURITY");
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("microloan")
            .withUsername("microloan")
            .withPassword("microloan");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        // 动态覆盖只指向本测试容器，真实向量写入不会污染开发、测试或生产数据库。
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private KnowledgeMapper knowledgeMapper;
    @Autowired
    private KnowledgePublicationService publicationService;
    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private KnowledgeEmbeddingService embeddingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void embedding3IndexesPublishedKnowledgeAndImprovesSemanticRecall() throws Exception {
        // 先用独立探针锁定供应商实际维度，避免批量调用后才在 pgvector 写入阶段发现漂移。
        float[] probe = embeddingModel.embed("验证真实向量维度");
        assertThat(probe).hasSize(EXPECTED_DIMENSIONS);

        for (String documentNo : DOCUMENTS) {
            // classpath 导入器只创建 DRAFT；显式审批和发布复现客户知识进入召回前的治理门禁。
            publicationService.approve(documentNo, "2026.1", "real-embedding-evaluation");
            publicationService.publish(documentNo, "2026.1");
        }
        // 管理 API 与启动索引器共用该服务，评测同时验证生产索引实现而非测试专用逻辑。
        KnowledgeEmbeddingIndexResult indexResult = embeddingService.indexPendingChunks();
        assertThat(indexResult.model()).isEqualTo(MODEL);
        assertThat(indexResult.dimensions()).isEqualTo(EXPECTED_DIMENSIONS);

        int indexedChunks = Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_chunk WHERE embedding_model = ? AND embedding IS NOT NULL",
                Integer.class, MODEL));
        int totalChunks = Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_chunk", Integer.class));
        assertThat(indexedChunks).isEqualTo(totalChunks);

        byte[] datasetBytes;
        try (InputStream input = Objects.requireNonNull(
                getClass().getResourceAsStream("/evaluation/embedding-hybrid-baseline.json"))) {
            datasetBytes = input.readAllBytes();
        }
        JsonNode cases = new ObjectMapper().readTree(datasetBytes);
        ArrayNode caseResults = new ObjectMapper().createArrayNode();
        int recalled = 0;
        int fullTextMissesRecovered = 0;
        for (JsonNode evaluationCase : cases) {
            String question = evaluationCase.path("question").asText();
            String productCode = evaluationCase.path("productCode").asText();
            String expectedDocumentId = evaluationCase.path("expectedDocumentId").asText();
            List<KnowledgeEvidence> fullText = knowledgeMapper.searchPublished(
                    question, productCode, "ALL", 20);
            // 口语化基线故意让全文路零命中；KnowledgeService 命中才可证明向量路提供了增量价值。
            KnowledgeAnswer answer = knowledgeService.answer(question, productCode, "ALL");
            boolean expectedRecalled = answer.sources().stream()
                    .anyMatch(source -> source.documentId().equals(expectedDocumentId));
            recalled += expectedRecalled ? 1 : 0;
            fullTextMissesRecovered += fullText.isEmpty() && expectedRecalled ? 1 : 0;

            ObjectNode result = caseResults.addObject();
            result.put("caseId", evaluationCase.path("id").asText());
            result.put("expectedDocumentId", expectedDocumentId);
            result.put("fullTextHitCount", fullText.size());
            result.put("hybridRecalled", expectedRecalled);
            ArrayNode sources = result.putArray("hybridSources");
            answer.sources().forEach(source -> sources.add(source.documentId()));
        }

        double recallRate = (double) recalled / cases.size();
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode report = objectMapper.createObjectNode();
        // 绑定数据集哈希、模型和维度，使后续更换知识或模型时的召回率可以正确归因。
        report.put("schemaVersion", "embedding-hybrid-evaluation-v1");
        report.put("generatedAt", Instant.now().toString());
        report.put("provider", PROVIDER);
        report.put("modelVersion", MODEL);
        report.put("dimensions", probe.length);
        report.put("dataset", "embedding-hybrid-baseline.json");
        report.put("datasetSha256", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(datasetBytes)));
        report.put("indexedChunks", indexedChunks);
        report.put("evaluatedCases", cases.size());
        report.put("recalledCases", recalled);
        report.put("recallRate", recallRate);
        report.put("fullTextMissesRecovered", fullTextMissesRecovered);
        report.set("cases", caseResults);

        Path reportDirectory = Path.of("target", "evaluation");
        Files.createDirectories(reportDirectory);
        Path reportPath = reportDirectory.resolve(
                "embedding-hybrid--" + MODEL + "--" + FILE_TIME.format(Instant.now()) + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        // 报告先落盘再执行质量门槛，失败样本仍可用于调优查询归一化、切片或重排。
        assertThat(recallRate)
                .as("真实混合召回率未达 100%%，完整报告：%s", reportPath)
                .isEqualTo(1.0);
        assertThat(fullTextMissesRecovered)
                .as("评测必须证明向量路召回了全文路未命中的样本，完整报告：%s", reportPath)
                .isPositive();
        // 错配产品时仍必须由 SQL 元数据过滤阻止经营贷证据越界。
        assertThat(knowledgeService.answer("开小店的老板办借款要带哪些证明？",
                "PERSONAL_CONSUMER", "ALL").sources())
                .noneMatch(source -> source.documentId().startsWith("KB-MB-"));
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
