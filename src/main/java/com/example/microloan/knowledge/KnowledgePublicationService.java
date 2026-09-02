package com.example.microloan.knowledge;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * 知识版本状态机服务。
 *
 * <p>每次迁移都把期望前置状态写入 UPDATE 条件，使状态检查和更新成为同一个数据库原子操作；并发请求
 * 只有一个能够成功。服务不修改正文或切片，客户检索是否可见由 PUBLISHED 状态即时决定。</p>
 */
public class KnowledgePublicationService {

    private final KnowledgeMapper knowledgeMapper;

    public KnowledgePublicationService(KnowledgeMapper knowledgeMapper) {
        this.knowledgeMapper = knowledgeMapper;
    }

    @Transactional
    /** 执行 DRAFT → APPROVED，并记录非空审核人。 */
    public KnowledgeDocumentIdentity approve(String documentNo, String version, String reviewer) {
        if (reviewer == null || reviewer.isBlank()) {
            throw new IllegalArgumentException("知识审核人不能为空");
        }
        return transition(documentNo, version, KnowledgeDocumentStatus.DRAFT,
                KnowledgeDocumentStatus.APPROVED, reviewer.strip());
    }

    @Transactional
    /** 执行 APPROVED → PUBLISHED，使该版本具备进入客户召回的必要状态。 */
    public KnowledgeDocumentIdentity publish(String documentNo, String version) {
        return transition(documentNo, version, KnowledgeDocumentStatus.APPROVED,
                KnowledgeDocumentStatus.PUBLISHED, null);
    }

    @Transactional
    /** 执行 PUBLISHED → EXPIRED，使该版本立即退出客户召回。 */
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
