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
/**
 * 知识治理管理接口，串联“导入草稿—审批—发布/下线—向量索引”生命周期。
 *
 * <p>安全层要求独立的知识管理员密钥。Controller 只处理 HTTP/文件协议，内容校验和事务边界分别下沉到
 * {@link KnowledgeImportService}、{@link KnowledgePublicationService} 和
 * {@link KnowledgeEmbeddingService}。</p>
 */
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
    /** 解析带元数据头的 Markdown，并事务化写入文档及切片草稿。 */
    public KnowledgeImportResult importMarkdown(@Valid @RequestBody MarkdownImportRequest request) {
        // 导入永远只产生 DRAFT，上传动作不能绕过后续独立审批与发布门禁。
        return importService.importMarkdownDraft(request.markdown());
    }

    @PostMapping(value = "/documents/pdf", consumes = "multipart/form-data")
    /** 校验并解析文本型 PDF，将独立提交的元数据与正文合成为草稿版本。 */
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
    /** 将指定版本从 DRAFT 原子迁移到 APPROVED，并固化审核人和审核时间。 */
    public KnowledgeDocumentIdentity approve(@PathVariable @NotBlank String documentNo,
                                             @PathVariable @NotBlank String version,
                                             @Valid @RequestBody ApprovalRequest request) {
        return publicationService.approve(documentNo, version, request.reviewer());
    }

    @PostMapping("/documents/{documentNo}/versions/{version}/publish")
    /** 将已审批版本发布；只有 PUBLISHED 才能被客户检索 SQL 命中。 */
    public KnowledgeDocumentIdentity publish(@PathVariable @NotBlank String documentNo,
                                             @PathVariable @NotBlank String version) {
        return publicationService.publish(documentNo, version);
    }

    @PostMapping("/documents/{documentNo}/versions/{version}/expire")
    /** 下线已发布版本；状态变更后立即从客户召回集合中排除。 */
    public KnowledgeDocumentIdentity expire(@PathVariable @NotBlank String documentNo,
                                            @PathVariable @NotBlank String version) {
        return publicationService.expire(documentNo, version);
    }

    @PostMapping("/embeddings/index")
    /** 为缺失或模型版本过旧的切片补建向量，可在部分成功后重复调用。 */
    public KnowledgeEmbeddingIndexResult indexEmbeddings() {
        // 索引操作按切片和模型版本幂等，可在供应商限流或进程中断后由管理员安全重试。
        return embeddingService.indexPendingChunks();
    }

    public record MarkdownImportRequest(
            /** 包含受控元数据头和正文的完整 Markdown。 */
            @NotBlank @Size(max = 1_048_576) String markdown
    ) {
    }

    public record ApprovalRequest(
            /** 对本知识版本承担审核责任的人员标识。 */
            @NotBlank @Size(max = 100) String reviewer
    ) {
    }

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
    ) {
    }
}
