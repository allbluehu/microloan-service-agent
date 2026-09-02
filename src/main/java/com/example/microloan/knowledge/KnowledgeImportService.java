package com.example.microloan.knowledge;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * 知识草稿导入事务门面。
 *
 * <p>Markdown 和 PDF 在各自解析器中转换为统一 KnowledgeMarkdownDocument，随后共同经过语义切片，
 * 最后在同一事务内写入文档与全部切片。导入仅产生 DRAFT，不隐式审批、发布或调用向量模型。</p>
 */
public class KnowledgeImportService {

    private final KnowledgeMarkdownParser markdownParser;
    private final KnowledgeMarkdownChunker markdownChunker;
    private final KnowledgePdfParser pdfParser;
    private final KnowledgeMapper knowledgeMapper;

    public KnowledgeImportService(KnowledgeMarkdownParser markdownParser,
                                  KnowledgeMarkdownChunker markdownChunker,
                                  KnowledgePdfParser pdfParser,
                                  KnowledgeMapper knowledgeMapper) {
        this.markdownParser = markdownParser;
        this.markdownChunker = markdownChunker;
        this.pdfParser = pdfParser;
        this.knowledgeMapper = knowledgeMapper;
    }

    @Transactional
    /** 解析元数据头和正文，校验后写入 Markdown 草稿。 */
    public KnowledgeImportResult importMarkdownDraft(String markdown) {
        return importDraft(markdownParser.parse(markdown));
    }

    @Transactional
    /** 完成 PDF 文件安全校验与文本提取，再按统一草稿链写入。 */
    public KnowledgeImportResult importPdfDraft(String filename, byte[] content, KnowledgePdfMetadata metadata) {
        return importDraft(pdfParser.parse(filename, content, metadata));
    }

    private KnowledgeImportResult importDraft(KnowledgeMarkdownDocument document) {
        List<KnowledgeChunkDraft> chunks = markdownChunker.chunk(document);

        Long documentId = knowledgeMapper.insertDraftDocument(document);
        if (documentId == null) {
            // 文档号与版本是不可变身份；相同内容幂等，不同内容必须升级版本后再导入。
            KnowledgeDocumentIdentity existing = knowledgeMapper.findDocumentIdentity(
                    document.documentNo(), document.version());
            if (existing == null) {
                throw new IllegalStateException("知识文档并发导入后未能读取既有版本");
            }
            if (!existing.checksum().equals(document.checksum())) {
                throw new IllegalStateException("相同知识文档版本已存在不同内容，请提升版本号后重新导入");
            }
            return new KnowledgeImportResult(existing.id(), existing.documentNo(), existing.version(),
                    knowledgeMapper.countChunks(existing.id()), true);
        }

        for (KnowledgeChunkDraft chunk : chunks) {
            knowledgeMapper.insertChunk(documentId, chunk);
        }
        return new KnowledgeImportResult(documentId, document.documentNo(), document.version(),
                chunks.size(), false);
    }
}
