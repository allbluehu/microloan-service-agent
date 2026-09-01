CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    document_no VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    region VARCHAR(50) NOT NULL,
    version VARCHAR(30) NOT NULL,
    audience VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    reviewer VARCHAR(100),
    checksum VARCHAR(64) NOT NULL,
    CONSTRAINT uk_knowledge_document_version UNIQUE (document_no, version)
);

CREATE TABLE knowledge_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES knowledge_document(id),
    chunk_no VARCHAR(40) NOT NULL,
    section_path VARCHAR(300) NOT NULL,
    content TEXT NOT NULL,
    search_text TEXT NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', search_text)) STORED,
    CONSTRAINT uk_knowledge_chunk_no UNIQUE (document_id, chunk_no)
);

CREATE INDEX idx_knowledge_chunk_fts ON knowledge_chunk USING GIN(search_vector);
CREATE INDEX idx_knowledge_chunk_trgm ON knowledge_chunk USING GIN(search_text gin_trgm_ops);

INSERT INTO knowledge_document
    (id, document_no, title, product_code, region, version, audience, status,
     effective_from, effective_to, reviewer, checksum)
VALUES
    (10001, 'KD-MB-001', '个体工商户经营贷申请指南', 'MICRO_BUSINESS', 'ALL', '2026.1',
     'PUBLIC', 'PUBLISHED', DATE '2026-01-01', NULL, 'demo-reviewer', 'demo-current-001'),
    (10002, 'KD-MB-001', '个体工商户经营贷申请指南（旧版）', 'MICRO_BUSINESS', 'ALL', '2025.1',
     'PUBLIC', 'EXPIRED', DATE '2025-01-01', DATE '2025-12-31', 'demo-reviewer', 'demo-expired-001'),
    (10003, 'KD-MB-INTERNAL', '经营贷内部审核说明', 'MICRO_BUSINESS', 'ALL', '2026.1',
     'INTERNAL', 'PUBLISHED', DATE '2026-01-01', NULL, 'demo-reviewer', 'demo-internal-001'),
    (10004, 'KD-PC-001', '个人消费贷申请指南', 'PERSONAL_CONSUMER', 'ALL', '2026.1',
     'PUBLIC', 'PUBLISHED', DATE '2026-01-01', NULL, 'demo-reviewer', 'demo-consumer-001');

INSERT INTO knowledge_chunk
    (document_id, chunk_no, section_path, content, search_text)
VALUES
    (10001, 'KC-MB-001', '申请指南/申请材料',
     '个体工商户经营贷申请时，需要准备本人有效身份证件、营业执照和用于经营情况核验的材料。具体补充材料以申请页面提示为准。',
     '经营贷 材料 申请材料 个体工商户 营业执照 身份证件'),
    (10001, 'KC-MB-002', '申请指南/贷款期限',
     '个体工商户经营贷可选择的期限最长为24期，实际可选期限以申请页面展示结果为准。',
     '经营贷 期限 多少期 最长 24期'),
    (10002, 'KC-MB-OLD-001', '旧版/申请材料',
     '旧版经营贷只需要营业执照，无需其他材料。',
     '经营贷 材料 申请材料 旧版'),
    (10003, 'KC-MB-INTERNAL-001', '内部规则/审核',
     '内部反欺诈审核阈值为仅供内部使用的模拟信息。',
     '经营贷 内部 审核 阈值'),
    (10004, 'KC-PC-001', '申请指南/申请材料',
     '个人消费贷申请材料以本人身份证件和申请页面提示为准。',
     '消费贷 材料 申请材料 身份证件');

SELECT setval(pg_get_serial_sequence('knowledge_document', 'id'), (SELECT MAX(id) FROM knowledge_document));
