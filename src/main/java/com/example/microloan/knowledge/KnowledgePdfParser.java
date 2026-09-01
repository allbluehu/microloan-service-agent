package com.example.microloan.knowledge;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KnowledgePdfParser {

    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final Set<String> AUDIENCES = Set.of("PUBLIC", "CUSTOMER", "INTERNAL");

    private final int maxBytes;
    private final int maxPages;

    public KnowledgePdfParser(
            @Value("${knowledge.ingestion.pdf-max-bytes:10485760}") int maxBytes,
            @Value("${knowledge.ingestion.pdf-max-pages:100}") int maxPages) {
        if (maxBytes <= 0 || maxPages <= 0) {
            throw new IllegalArgumentException("PDF 大小和页数上限必须大于 0");
        }
        this.maxBytes = maxBytes;
        this.maxPages = maxPages;
    }

    public KnowledgeMarkdownDocument parse(String filename, byte[] content, KnowledgePdfMetadata metadata) {
        validateFile(filename, content);
        validateMetadata(metadata);
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("不允许导入加密 PDF");
            }
            if (!document.getCurrentAccessPermission().canExtractContent()) {
                throw new IllegalArgumentException("PDF 未授权提取文本");
            }
            if (document.getNumberOfPages() == 0 || document.getNumberOfPages() > maxPages) {
                throw new IllegalArgumentException("PDF 页数必须在 1 到 " + maxPages + " 之间");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = normalizeText(stripper.getText(document));
            if (text.isBlank()) {
                throw new IllegalArgumentException("PDF 未提取到文本，暂不支持纯扫描件");
            }
            return new KnowledgeMarkdownDocument(
                    metadata.documentNo(), metadata.title(), metadata.productCode(), metadata.region(),
                    metadata.version(), metadata.audience().toUpperCase(Locale.ROOT), metadata.effectiveFrom(),
                    metadata.effectiveTo(), metadata.reviewer(), List.copyOf(metadata.keywords()), text,
                    KnowledgeChecksum.sha256(content));
        } catch (IOException exception) {
            throw new IllegalArgumentException("PDF 文件损坏或格式不受支持", exception);
        }
    }

    private void validateFile(String filename, byte[] content) {
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("文件扩展名必须为 .pdf");
        }
        if (content == null || content.length < PDF_SIGNATURE.length || content.length > maxBytes) {
            throw new IllegalArgumentException("PDF 文件为空或超过大小上限");
        }
        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (content[index] != PDF_SIGNATURE[index]) {
                throw new IllegalArgumentException("文件签名不是有效 PDF");
            }
        }
    }

    private void validateMetadata(KnowledgePdfMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("PDF 知识元数据不能为空");
        }
        requireText(metadata.documentNo(), "documentNo");
        requireText(metadata.title(), "title");
        requireText(metadata.productCode(), "productCode");
        requireText(metadata.region(), "region");
        requireText(metadata.version(), "version");
        requireText(metadata.reviewer(), "reviewer");
        String audience = requireText(metadata.audience(), "audience").toUpperCase(Locale.ROOT);
        if (!AUDIENCES.contains(audience)) {
            throw new IllegalArgumentException("audience 仅支持 PUBLIC、CUSTOMER 或 INTERNAL");
        }
        LocalDate effectiveFrom = metadata.effectiveFrom();
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("缺少知识元数据：effectiveFrom");
        }
        if (metadata.effectiveTo() != null && metadata.effectiveTo().isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo 不能早于 effectiveFrom");
        }
        if (metadata.keywords() == null || metadata.keywords().isEmpty()
                || metadata.keywords().stream().anyMatch(keyword -> keyword == null || keyword.isBlank())) {
            throw new IllegalArgumentException("keywords 至少包含一个有效关键词");
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少知识元数据：" + field);
        }
        return value.strip();
    }

    private String normalizeText(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (Character.isISOControl(character) && character != '\n' && character != '\t') {
                throw new IllegalArgumentException("PDF 文本包含不允许的控制字符");
            }
        }
        return normalized;
    }
}
