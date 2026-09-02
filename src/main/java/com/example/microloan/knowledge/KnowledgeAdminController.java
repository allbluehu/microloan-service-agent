package com.example.microloan.knowledge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/knowledge")
@Validated
public class KnowledgeAdminController {

    private final KnowledgeImportService importService;
    private final KnowledgePublicationService publicationService;
    private final KnowledgeEmbeddingService embeddingService;

    public KnowledgeAdminController(KnowledgeImportService importService,
                                    KnowledgePublicationService publicationService,
                                    KnowledgeEmbeddingService embeddingService) {
        this.importService = importService;
        this.publicationService = publicationService;
        this.embeddingService = embeddingService;
    }

    @PostMapping("/documents/markdown")
    public KnowledgeImportResult importMarkdown(@Valid @RequestBody MarkdownImportRequest request) {
        // 导入永远只产生 DRAFT，上传动作不能绕过后续独立审批与发布门禁。
        return importService.importMarkdownDraft(request.markdown());
    }

    @PostMapping(value = "/documents/pdf", consumes = "multipart/form-data")
    public KnowledgeImportResult importPdf(@RequestPart("file") MultipartFile file,
                                           @Valid @RequestPart("metadata") PdfImportMetadataRequest request)
            throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("PDF 文件不能为空");
        }
        KnowledgePdfMetadata metadata = new KnowledgePdfMetadata(
                request.documentNo(), request.title(), request.productCode(), request.region(), request.version(),
                request.audience(), request.effectiveFrom(), request.effectiveTo(), null, request.keywords());
        return importService.importPdfDraft(file.getOriginalFilename(), file.getBytes(), metadata);
    }

    @PostMapping("/documents/{documentNo}/versions/{version}/approve")
    public KnowledgeDocumentIdentity approve(@PathVariable @NotBlank String documentNo,
                                             @PathVariable @NotBlank String version,
                                             @Valid @RequestBody ApprovalRequest request) {
        return publicationService.approve(documentNo, version, request.reviewer());
    }

    @PostMapping("/documents/{documentNo}/versions/{version}/publish")
    public KnowledgeDocumentIdentity publish(@PathVariable @NotBlank String documentNo,
                                             @PathVariable @NotBlank String version) {
        return publicationService.publish(documentNo, version);
    }

    @PostMapping("/documents/{documentNo}/versions/{version}/expire")
    public KnowledgeDocumentIdentity expire(@PathVariable @NotBlank String documentNo,
                                            @PathVariable @NotBlank String version) {
        return publicationService.expire(documentNo, version);
    }

    @PostMapping("/embeddings/index")
    public KnowledgeEmbeddingIndexResult indexEmbeddings() {
        // 索引操作按切片和模型版本幂等，可在供应商限流或进程中断后由管理员安全重试。
        return embeddingService.indexPendingChunks();
    }

    public record MarkdownImportRequest(
            /** 包含受控元数据头和正文的完整 Markdown。 */
            @NotBlank @Size(max = 1_048_576) String markdown
    ) {}

    public record ApprovalRequest(
            /** 对本知识版本承担审核责任的人员标识。 */
            @NotBlank @Size(max = 100) String reviewer
    ) {}

    public record PdfImportMetadataRequest(
            /** 知识文档的稳定业务编号。 */
            @NotBlank @Size(max = 100) String documentNo,
            /** 面向审核人与客户展示的标题。 */
            @NotBlank @Size(max = 200) String title,
            /** 文档适用的产品编码。 */
            @NotBlank @Size(max = 50) String productCode,
            /** 文档适用地区，ALL 表示全部地区。 */
            @NotBlank @Size(max = 50) String region,
            /** 不可覆盖的知识版本号。 */
            @NotBlank @Size(max = 50) String version,
            /** 访问范围，只允许 PUBLIC、CUSTOMER 或 INTERNAL。 */
            @NotBlank @Size(max = 20) String audience,
            /** 知识开始生效的日期。 */
            LocalDate effectiveFrom,
            /** 知识停止生效的日期，可为空。 */
            LocalDate effectiveTo,
            /** 用于全文检索的受控业务关键词。 */
            @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 50) String> keywords
    ) {}
}
