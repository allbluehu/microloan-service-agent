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

class KnowledgeMarkdownParserTest {

    private final KnowledgeMarkdownParser parser = new KnowledgeMarkdownParser();

    @Test
    void parsesRequiredMetadataAndNormalizesLineEndingsForChecksum() {
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
