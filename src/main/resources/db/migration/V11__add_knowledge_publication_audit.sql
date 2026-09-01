ALTER TABLE knowledge_document
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD COLUMN expired_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_knowledge_document_status
        CHECK (status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'EXPIRED'));

-- 迁移前已发布或过期的演示数据没有历史事件时间，以迁移时刻作为审计起点。
UPDATE knowledge_document
SET approved_at = CURRENT_TIMESTAMP,
    published_at = CURRENT_TIMESTAMP
WHERE status IN ('PUBLISHED', 'EXPIRED');

UPDATE knowledge_document
SET expired_at = CURRENT_TIMESTAMP
WHERE status = 'EXPIRED';
