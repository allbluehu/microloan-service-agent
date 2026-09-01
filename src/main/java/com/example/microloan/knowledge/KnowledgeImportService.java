package com.example.microloan.knowledge;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeImportService {

    private final KnowledgeMarkdownParser markdownParser;
    private final KnowledgeMarkdownChunker markdownChunker;
    private final KnowledgeMapper knowledgeMapper;

    public KnowledgeImportService(KnowledgeMarkdownParser markdownParser,
                                  KnowledgeMarkdownChunker markdownChunker,
                                  KnowledgeMapper knowledgeMapper) {
        this.markdownParser = markdownParser;
        this.markdownChunker = markdownChunker;
        this.knowledgeMapper = knowledgeMapper;
    }

    @Transactional
    public KnowledgeImportResult importMarkdownDraft(String markdown) {
        KnowledgeMarkdownDocument document = markdownParser.parse(markdown);
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
