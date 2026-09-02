package com.example.microloan.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;

/**
 * PDF 知识入口的格式与安全边界测试。
 * 调用链为“上传字节 → 扩展名/魔数/大小检查 → PDFBox 权限与页数检查 → 文本提取 → 统一知识文档模型”，
 * 二进制校验和用于版本幂等，不能被提取文本或 PDF 元数据替代。
 */
class KnowledgePdfParserTest {

    private final KnowledgePdfParser parser = new KnowledgePdfParser(1024 * 1024, 10);

    @Test
    void extractsTextAndPreservesBinaryChecksum() throws IOException {
        // 文件名大小写不影响格式识别，但内容身份必须严格绑定原始 PDF 字节。
        byte[] pdf = createPdf("Micro business loan application materials", 1, false);

        KnowledgeMarkdownDocument document = parser.parse("guide.PDF", pdf, metadata());

        assertThat(document.body()).contains("Micro business loan application materials");
        assertThat(document.checksum()).isEqualTo(KnowledgeChecksum.sha256(pdf));
        assertThat(document.keywords()).containsExactly("loan", "materials");
    }

    @Test
    void rejectsWrongSignatureAndOversizedFile() {
        // 扩展名不足以证明格式，且大小限制应在 PDFBox 深度解析之前阻断资源消耗。
        assertThatThrownBy(() -> parser.parse("guide.pdf", "not-pdf".getBytes(), metadata()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("签名");

        KnowledgePdfParser tinyLimit = new KnowledgePdfParser(5, 10);
        assertThatThrownBy(() -> tinyLimit.parse("guide.pdf", "%PDF-too-large".getBytes(), metadata()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("大小上限");
    }

    @Test
    void rejectsPageCountAboveConfiguredLimit() throws IOException {
        // 页数上限补充字节上限，防止高度压缩但解析成本很高的文档进入切片流程。
        byte[] pdf = createPdf("page text", 2, false);

        assertThatThrownBy(() -> new KnowledgePdfParser(1024 * 1024, 1)
                .parse("guide.pdf", pdf, metadata()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("页数");
    }

    @Test
    void rejectsEncryptedPdf() throws IOException {
        // 禁止绕过文档所有者设置的提取权限，也避免部署环境因密码差异产生不一致结果。
        byte[] pdf = createPdf("protected text", 1, true);

        assertThatThrownBy(() -> parser.parse("guide.pdf", pdf, metadata()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("加密 PDF");
    }

    @Test
    void rejectsPdfWithoutExtractableText() throws IOException {
        // 当前系统没有 OCR，空文本必须明确拒绝，不能导入一个永远无法召回的知识版本。
        byte[] pdf = createPdf(null, 1, false);

        assertThatThrownBy(() -> parser.parse("scan.pdf", pdf, metadata()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("纯扫描件");
    }

    private byte[] createPdf(String text, int pages, boolean encrypted) throws IOException {
        // 测试内生成最小 PDF，避免二进制夹具不可审查，并可精确控制页数、文本和权限。
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 0; index < pages; index++) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (text != null) {
                    try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                        stream.beginText();
                        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        stream.newLineAtOffset(72, 720);
                        stream.showText(text + " " + index);
                        stream.endText();
                    }
                }
            }
            if (encrypted) {
                AccessPermission permission = new AccessPermission();
                permission.setCanExtractContent(false);
                document.protect(new StandardProtectionPolicy("owner-secret", "user-secret", permission));
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private KnowledgePdfMetadata metadata() {
        return new KnowledgePdfMetadata("KD-PDF-001", "PDF loan guide", "MICRO_BUSINESS", "ALL",
                "2026.1", "PUBLIC", LocalDate.of(2026, 9, 1), null, "pdf-reviewer",
                List.of("loan", "materials"));
    }
}
