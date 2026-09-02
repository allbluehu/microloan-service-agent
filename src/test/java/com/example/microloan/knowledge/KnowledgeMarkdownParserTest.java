package com.example.microloan.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.junit.jupiter.api.Test;

/**
 * Markdown 知识入库前的解析与切片测试。
 * 调用链为“原始受控文档 → 元数据校验与规范化校验和 → 标题/段落语义切片 → KnowledgeImportService 写入草稿”，
 * 本类在数据库之前锁定内容身份、适用范围和切片稳定性。
 */
class KnowledgeMarkdownParserTest {

    private final KnowledgeMarkdownParser parser = new KnowledgeMarkdownParser();

    @Test
    void parsesRequiredMetadataAndNormalizesLineEndingsForChecksum() {
        // Windows 与 Unix 换行必须得到同一内容身份，否则跨环境导入会被误判为不同版本。
        KnowledgeMarkdownDocument unix = parser.parse(markdown("\n"));
        KnowledgeMarkdownDocument windows = parser.parse(markdown("\r\n"));

        assertThat(unix.documentNo()).isEqualTo("KD-MB-010");
        assertThat(unix.audience()).isEqualTo("PUBLIC");
        assertThat(unix.effectiveFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(unix.effectiveTo()).isNull();
        assertThat(unix.keywords()).containsExactly("经营贷", "材料", "申请流程");
        assertThat(unix.checksum()).hasSize(64).isEqualTo(windows.checksum());
    }

    @Test
    void rejectsInvalidAudienceAndEffectiveDateRange() {
        // 受众和有效期决定客户是否可见，非法元数据必须在落库前失败而不是依赖查询阶段兜底。
        assertThatThrownBy(() -> parser.parse(markdown("\n").replace("audience: PUBLIC", "audience: SECRET")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audience");
        assertThatThrownBy(() -> parser.parse(markdown("\n").replace(
                "effectiveTo:", "effectiveTo: 2026-08-31")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("effectiveTo");
    }

    @Test
    void chunksByHeadingAndKeepsSearchKeywords() {
        // 标题路径用于引用展示，元数据关键词进入 searchText 以弥补正文词项不足。
        KnowledgeMarkdownDocument document = parser.parse(markdown("\n"));
        KnowledgeMarkdownChunker chunker = new KnowledgeMarkdownChunker(800);

        List<KnowledgeChunkDraft> chunks = chunker.chunk(document);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(KnowledgeChunkDraft::sectionPath)
                .containsExactly("经营贷指南/申请材料", "经营贷指南/办理流程");
        assertThat(chunks.getFirst().searchText()).contains("MICRO_BUSINESS", "经营贷 材料 申请流程");
        assertThat(chunks.getFirst().checksum()).hasSize(64);
    }

    @Test
    void oversizedParagraphPrefersChineseSentenceBoundary() {
        // 超长段落优先沿中文句号切分，既满足模型输入上限，也避免把完整语义句截断。
        String firstSentence = "这是第一段较长的申请材料说明".repeat(6) + "。";
        String secondSentence = "这是第二段较长的补充材料说明".repeat(6) + "。";
        KnowledgeMarkdownDocument document = parser.parse(
                markdown("\n").replace("需要营业执照和身份证件。", firstSentence + secondSentence));

        List<KnowledgeChunkDraft> chunks = new KnowledgeMarkdownChunker(120).chunk(document);

        assertThat(chunks.size()).isGreaterThan(2);
        assertThat(chunks).allMatch(chunk -> chunk.content().length() <= 120);
        assertThat(chunks.getFirst().content()).endsWith("。");
    }

    @Test
    void allClasspathKnowledgeAssetsAreValidAndUniquelyVersioned() throws Exception {
        // 扫描真实仓库资产而非测试夹具，防止新增文档绕过解析约束或复用已有文档号版本。
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:knowledge/*.md");
        KnowledgeMarkdownChunker chunker = new KnowledgeMarkdownChunker(800);
        HashSet<String> identities = new HashSet<>();

        assertThat(resources).hasSize(10);
        for (Resource resource : resources) {
            KnowledgeMarkdownDocument document = parser.parse(
                    resource.getContentAsString(StandardCharsets.UTF_8));
            assertThat(identities.add(document.documentNo() + ":" + document.version()))
                    .as("文档号与版本必须唯一：%s", resource.getFilename())
                    .isTrue();
            assertThat(chunker.chunk(document)).isNotEmpty();
        }
    }

    private String markdown(String lineSeparator) {
        return String.join(lineSeparator,
                "---",
                "documentNo: KD-MB-010",
                "title: 经营贷指南",
                "productCode: MICRO_BUSINESS",
                "region: ALL",
                "version: 2026.2",
                "audience: PUBLIC",
                "effectiveFrom: 2026-09-01",
                "effectiveTo:",
                "reviewer: demo-reviewer",
                "keywords: 经营贷, 材料, 申请流程",
                "---",
                "# 经营贷指南",
                "## 申请材料",
                "需要营业执照和身份证件。",
                "",
                "## 办理流程",
                "提交申请后以页面展示结果为准。",
                "");
    }
}
