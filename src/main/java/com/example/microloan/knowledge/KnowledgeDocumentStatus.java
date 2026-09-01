package com.example.microloan.knowledge;

public enum KnowledgeDocumentStatus {
    /** 已导入但尚未完成合规审核。 */
    DRAFT,
    /** 已通过审核但尚未对客发布。 */
    APPROVED,
    /** 已发布且可在有效期内参与检索。 */
    PUBLISHED,
    /** 已下线且不得参与客户检索。 */
    EXPIRED
}
