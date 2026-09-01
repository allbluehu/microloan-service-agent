package com.example.microloan.knowledge;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgePublicationService {

    private final KnowledgeMapper knowledgeMapper;

    public KnowledgePublicationService(KnowledgeMapper knowledgeMapper) {
        this.knowledgeMapper = knowledgeMapper;
    }

    @Transactional
    public KnowledgeDocumentIdentity approve(String documentNo, String version, String reviewer) {
        if (reviewer == null || reviewer.isBlank()) {
            throw new IllegalArgumentException("知识审核人不能为空");
        }
        return transition(documentNo, version, KnowledgeDocumentStatus.DRAFT,
                KnowledgeDocumentStatus.APPROVED, reviewer.strip());
    }

    @Transactional
    public KnowledgeDocumentIdentity publish(String documentNo, String version) {
        return transition(documentNo, version, KnowledgeDocumentStatus.APPROVED,
                KnowledgeDocumentStatus.PUBLISHED, null);
    }

    @Transactional
    public KnowledgeDocumentIdentity expire(String documentNo, String version) {
        return transition(documentNo, version, KnowledgeDocumentStatus.PUBLISHED,
                KnowledgeDocumentStatus.EXPIRED, null);
    }

    private KnowledgeDocumentIdentity transition(String documentNo, String version,
                                                  KnowledgeDocumentStatus expected,
                                                  KnowledgeDocumentStatus next,
                                                  String reviewer) {
        int updated = knowledgeMapper.transitionDocumentStatus(
                documentNo, version, expected.name(), next.name(), reviewer);
        if (updated == 0) {
            KnowledgeDocumentIdentity current = knowledgeMapper.findDocumentIdentity(documentNo, version);
            if (current == null) {
                throw new IllegalArgumentException("知识文档版本不存在");
            }
            // 带前置状态的更新同时承担并发控制，失败时返回实际状态便于审核人员处理。
            throw new IllegalStateException("知识文档状态必须为%s，当前为%s"
                    .formatted(expected, current.status()));
        }
        return knowledgeMapper.findDocumentIdentity(documentNo, version);
    }
}
