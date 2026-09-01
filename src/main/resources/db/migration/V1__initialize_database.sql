CREATE TABLE customer (
    id BIGSERIAL PRIMARY KEY,                              -- 客户数据库主键
    customer_no VARCHAR(32) NOT NULL UNIQUE,              -- 对外使用的唯一客户编号
    name VARCHAR(100) NOT NULL,                           -- 客户展示名称
    password_hash VARCHAR(100) NOT NULL,                  -- BCrypt 密码摘要，不存储明文密码
    mobile_mask VARCHAR(32) NOT NULL,                     -- 脱敏后的手机号码
    status VARCHAR(20) NOT NULL,                          -- 客户账户状态，如 ACTIVE
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP -- 客户记录创建时间
);

CREATE TABLE loan (
    id BIGSERIAL PRIMARY KEY,                              -- 贷款数据库主键
    loan_no VARCHAR(32) NOT NULL UNIQUE,                  -- 对外使用的唯一贷款编号
    customer_id BIGINT NOT NULL REFERENCES customer(id), -- 贷款所属客户主键
    principal NUMERIC(18, 2) NOT NULL,                    -- 贷款合同初始本金，单位为元
    annual_rate NUMERIC(10, 6) NOT NULL,                  -- 合同年化利率，小数形式
    term INTEGER NOT NULL,                                -- 贷款总期数
    status VARCHAR(20) NOT NULL,                          -- 贷款当前状态，如 ACTIVE
    version INTEGER NOT NULL DEFAULT 0,                   -- 贷款记录乐观锁版本号
    remaining_principal NUMERIC(18, 2),                   -- 提前结清试算使用的剩余本金，初始化后设为非空
    interest_accrued_through DATE                         -- 利息已计提截止日期，初始化后设为非空
);

CREATE TABLE repayment_plan (
    id BIGSERIAL PRIMARY KEY,                              -- 还款计划数据库主键
    loan_id BIGINT NOT NULL REFERENCES loan(id),          -- 还款计划所属贷款主键
    period_no INTEGER NOT NULL,                           -- 贷款内的还款期次
    due_date DATE NOT NULL,                               -- 本期计划还款日期
    principal NUMERIC(18, 2) NOT NULL,                    -- 本期应还本金，单位为元
    interest NUMERIC(18, 2) NOT NULL,                     -- 本期应还利息，单位为元
    fee NUMERIC(18, 2) NOT NULL DEFAULT 0,                -- 本期应还费用，单位为元
    paid_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,        -- 本期累计实还金额，单位为元
    status VARCHAR(20) NOT NULL,                          -- 还款计划状态，如 PENDING
    CONSTRAINT uk_repayment_plan_period UNIQUE (loan_id, period_no) -- 同一贷款的还款期次不可重复
);

CREATE INDEX idx_loan_customer ON loan(customer_id);
CREATE INDEX idx_repayment_plan_next ON repayment_plan(loan_id, due_date);

-- 两个演示客户统一使用 Demo123!，数据库中仅保存 BCrypt 摘要。
INSERT INTO customer (id, customer_no, name, password_hash, mobile_mask, status)
VALUES
    (10001, 'C10001', '张三', '{bcrypt}$2y$12$RyawfIWNFhQjgQT.TJhRleFYBs7zn6S5nASOrzqpM5.ysQHfWUnfm', '138****0001', 'ACTIVE'),
    (10002, 'C10002', '李四', '{bcrypt}$2y$12$RyawfIWNFhQjgQT.TJhRleFYBs7zn6S5nASOrzqpM5.ysQHfWUnfm', '138****0002', 'ACTIVE');

INSERT INTO loan (id, loan_no, customer_id, principal, annual_rate, term, status)
VALUES
    (10001, 'L10001', 10001, 12000.00, 0.075000, 12, 'ACTIVE'),
    (99999, 'L99999', 10002, 8000.00, 0.068000, 12, 'ACTIVE');

INSERT INTO repayment_plan
    (loan_id, period_no, due_date, principal, interest, fee, paid_amount, status)
VALUES
    (10001, 1, DATE '2026-09-15', 1200.00, 62.50, 0.00, 0.00, 'PENDING'),
    (99999, 1, DATE '2026-09-18', 800.00, 45.33, 0.00, 0.00, 'PENDING');

SELECT setval(pg_get_serial_sequence('customer', 'id'), (SELECT MAX(id) FROM customer));
SELECT setval(pg_get_serial_sequence('loan', 'id'), (SELECT MAX(id) FROM loan));

CREATE TABLE loan_application (
    id BIGSERIAL PRIMARY KEY,                              -- 贷款申请数据库主键
    application_no VARCHAR(32) NOT NULL UNIQUE,           -- 对外使用的唯一申请编号
    customer_id BIGINT NOT NULL REFERENCES customer(id), -- 申请所属客户主键
    product_code VARCHAR(50) NOT NULL,                    -- 申请对应的贷款产品编码
    status VARCHAR(30) NOT NULL,                          -- 申请当前审批状态
    reason_code VARCHAR(50),                              -- 审批状态对应的原因码
    missing_documents JSONB NOT NULL DEFAULT '[]'::jsonb, -- 仍需客户补充的材料 JSON 数组
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP -- 申请状态最后更新时间
);

CREATE INDEX idx_loan_application_customer ON loan_application(customer_id);

INSERT INTO loan_application
    (id, application_no, customer_id, product_code, status, reason_code, missing_documents, updated_at)
VALUES
    (10001, 'A10001', 10001, 'MICRO_BUSINESS', 'PENDING_DOCUMENT', NULL,
     '["营业执照"]'::jsonb, TIMESTAMPTZ '2026-08-30 10:00:00+08'),
    (99999, 'A99999', 10002, 'PERSONAL_CONSUMER', 'REVIEWING', NULL,
     '[]'::jsonb, TIMESTAMPTZ '2026-08-30 11:00:00+08');

SELECT setval(pg_get_serial_sequence('loan_application', 'id'), (SELECT MAX(id) FROM loan_application));

CREATE TABLE payment_record (
    id BIGSERIAL PRIMARY KEY,                              -- 支付流水数据库主键
    payment_no VARCHAR(40) NOT NULL UNIQUE,               -- 对外使用的唯一支付流水号
    loan_id BIGINT NOT NULL REFERENCES loan(id),          -- 支付流水关联的贷款主键
    customer_id BIGINT NOT NULL REFERENCES customer(id), -- 支付流水所属客户主键，用于强制数据隔离
    amount NUMERIC(18, 2) NOT NULL,                       -- 本次支付金额，单位为元
    channel VARCHAR(30) NOT NULL,                         -- 支付渠道编码，如 BANK_CARD
    bank_card_last_four CHAR(4),                          -- 扣款银行卡后四位，不存储完整卡号
    status VARCHAR(20) NOT NULL,                          -- 支付处理状态，如 SUCCESS 或 PROCESSING
    paid_at TIMESTAMPTZ NOT NULL                          -- 支付发生或进入处理状态的时间
);

CREATE INDEX idx_payment_record_loan_time ON payment_record(loan_id, paid_at DESC);

INSERT INTO payment_record
    (payment_no, loan_id, customer_id, amount, channel, bank_card_last_four, status, paid_at)
VALUES
    ('P10001', 10001, 10001, 1262.50, 'BANK_CARD', '1234', 'SUCCESS',
     TIMESTAMPTZ '2026-08-15 09:30:00+08'),
    ('P10002', 10001, 10001, 1262.50, 'BANK_CARD', '1234', 'PROCESSING',
     TIMESTAMPTZ '2026-08-30 14:20:00+08'),
    ('P99999', 99999, 10002, 845.33, 'BANK_CARD', '9876', 'SUCCESS',
     TIMESTAMPTZ '2026-08-18 11:00:00+08');

UPDATE loan
SET remaining_principal = CASE loan_no
        WHEN 'L10001' THEN 8200.00
        WHEN 'L99999' THEN 5600.00
        END,
    interest_accrued_through = DATE '2026-08-14';

ALTER TABLE loan
    ALTER COLUMN remaining_principal SET NOT NULL,
    ALTER COLUMN interest_accrued_through SET NOT NULL;

CREATE TABLE service_ticket (
    id BIGSERIAL PRIMARY KEY,                              -- 工单数据库主键
    ticket_no VARCHAR(40) NOT NULL UNIQUE,                -- 对外使用的唯一工单编号
    customer_id BIGINT NOT NULL REFERENCES customer(id), -- 工单所属客户主键
    conversation_id VARCHAR(64) NOT NULL,                 -- 创建工单的对话会话标识
    related_loan_id BIGINT REFERENCES loan(id),           -- 工单关联的贷款主键，可为空
    type VARCHAR(40) NOT NULL,                            -- 工单业务类型
    priority VARCHAR(20) NOT NULL,                        -- 工单处理优先级
    summary VARCHAR(500) NOT NULL,                        -- 经过空白规范化与长度限制的问题摘要
    status VARCHAR(20) NOT NULL,                          -- 工单当前处理状态
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,         -- 客户、会话、类型与关联资源组成的写入幂等键
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP -- 工单创建时间
);

CREATE INDEX idx_service_ticket_customer ON service_ticket(customer_id, created_at DESC);

CREATE TABLE chat_session (
    id BIGSERIAL PRIMARY KEY,                              -- 对话会话数据库主键
    session_id VARCHAR(64) NOT NULL UNIQUE,               -- 客户端使用的唯一会话标识
    customer_id BIGINT NOT NULL REFERENCES customer(id), -- 会话所属客户主键
    selected_loan_id VARCHAR(32),                         -- 最近一次明确选择的贷款编号
    selected_application_id VARCHAR(32),                  -- 最近一次明确选择的贷款申请编号
    selected_product_code VARCHAR(50),                    -- 最近一次明确选择的产品编码
    selected_region VARCHAR(50),                          -- 最近一次明确选择的地区编码
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 会话创建时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP  -- 会话最近消息时间
);

CREATE TABLE chat_message (
    id BIGSERIAL PRIMARY KEY,                              -- 对话消息数据库主键
    session_id BIGINT NOT NULL REFERENCES chat_session(id), -- 消息所属会话主键
    message_id VARCHAR(64) NOT NULL,                      -- 客户端消息标识或服务端派生消息标识
    role VARCHAR(20) NOT NULL,                            -- 消息角色：USER 或 ASSISTANT
    content VARCHAR(2000) NOT NULL,                       -- 脱敏并截断后的消息内容
    intent VARCHAR(50),                                   -- Assistant 消息对应的最终意图
    trace_id VARCHAR(64),                                 -- Assistant 消息对应的 Agent Trace 标识
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 消息创建时间
    CONSTRAINT ck_chat_message_role CHECK (role IN ('USER', 'ASSISTANT')), -- 限制受控消息角色
    CONSTRAINT uk_chat_message_identity UNIQUE (session_id, message_id, role) -- 同一会话内的角色消息不可重复
);

CREATE INDEX idx_chat_session_customer ON chat_session(customer_id, updated_at DESC);
CREATE INDEX idx_chat_message_session ON chat_message(session_id, created_at, id);

CREATE TABLE agent_trace (
    id BIGSERIAL PRIMARY KEY,                              -- Trace 记录数据库主键
    trace_id VARCHAR(64) NOT NULL,                        -- 单次 Agent 请求的全链路追踪标识
    session_id VARCHAR(64) NOT NULL,                      -- 当前对话会话标识
    customer_id BIGINT NOT NULL REFERENCES customer(id), -- Trace 所属客户主键，用于查询隔离
    node_sequence INTEGER NOT NULL,                       -- 节点在当前 Trace 内的单调递增顺序号
    node_name VARCHAR(50) NOT NULL,                       -- Agent 节点名称
    input_summary VARCHAR(300) NOT NULL,                  -- 已脱敏并截断的节点输入摘要
    output_summary VARCHAR(500),                          -- 已脱敏并截断的节点输出摘要
    model_version VARCHAR(100),                           -- 节点实际使用的模型版本，无模型调用时为空
    prompt_version VARCHAR(50),                           -- 节点实际使用的提示词版本，无提示词时为空
    latency_ms BIGINT NOT NULL,                           -- 节点执行耗时，单位为毫秒
    status VARCHAR(20) NOT NULL,                          -- 节点执行状态，如 SUCCESS 或 FAILED
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Trace 节点记录创建时间
    CONSTRAINT uk_agent_trace_sequence UNIQUE (trace_id, node_sequence) -- 同一 Trace 内的节点顺序不可重复
);

CREATE INDEX idx_agent_trace_customer ON agent_trace(customer_id, created_at DESC);

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_document (
    id BIGSERIAL PRIMARY KEY,                              -- 知识文档数据库主键
    document_no VARCHAR(40) NOT NULL,                     -- 跨版本稳定的知识文档业务编号
    title VARCHAR(200) NOT NULL,                          -- 知识文档标题
    product_code VARCHAR(50) NOT NULL,                    -- 知识文档适用的产品编码
    region VARCHAR(50) NOT NULL,                          -- 知识文档适用地区，ALL 表示全地区
    version VARCHAR(30) NOT NULL,                         -- 知识文档版本号
    audience VARCHAR(20) NOT NULL,                        -- 访问范围，如 PUBLIC、CUSTOMER 或 INTERNAL
    status VARCHAR(20) NOT NULL,                          -- 发布状态：DRAFT、APPROVED、PUBLISHED 或 EXPIRED
    effective_from DATE NOT NULL,                         -- 知识文档开始生效日期
    effective_to DATE,                                   -- 知识文档失效日期，长期有效时为空
    reviewer VARCHAR(100),                                -- 最近一次完成合规审核的审核人
    checksum VARCHAR(64) NOT NULL,                        -- 原始文档内容校验和，用于幂等和版本防覆盖
    approved_at TIMESTAMPTZ,                              -- 文档通过审核的时间
    published_at TIMESTAMPTZ,                             -- 文档正式发布的时间
    expired_at TIMESTAMPTZ,                               -- 文档下线失效的时间
    CONSTRAINT uk_knowledge_document_version UNIQUE (document_no, version), -- 同一业务编号下的版本号不可重复
    CONSTRAINT ck_knowledge_document_status CHECK (status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'EXPIRED')) -- 限制受控发布状态
);

CREATE TABLE knowledge_chunk (
    id BIGSERIAL PRIMARY KEY,                              -- 知识切片数据库主键
    document_id BIGINT NOT NULL REFERENCES knowledge_document(id), -- 切片所属知识文档主键
    chunk_no VARCHAR(40) NOT NULL,                        -- 文档内稳定且可重复生成的切片编号
    section_path VARCHAR(300) NOT NULL,                   -- 切片在原文中的标题层级路径
    content TEXT NOT NULL,                                -- 用于回答客户问题的切片正文
    search_text TEXT NOT NULL,                            -- 包含标题、路径和受控关键词的全文检索文本
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', search_text)) STORED, -- 自动生成的全文检索向量
    embedding VECTOR(1024),                               -- 1024 维知识切片向量，未生成时为空
    embedding_model VARCHAR(100),                         -- 生成当前向量的 Embedding 模型名称
    embedded_at TIMESTAMPTZ,                              -- 当前向量生成并写入数据库的时间
    checksum VARCHAR(64),                                 -- 切片正文校验和，初始化后设为非空
    CONSTRAINT uk_knowledge_chunk_no UNIQUE (document_id, chunk_no) -- 同一文档内的切片编号不可重复
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

CREATE INDEX idx_knowledge_chunk_embedding_hnsw
    ON knowledge_chunk USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;

-- 初始化脚本中的演示切片使用数据库摘要；应用导入的新切片统一写入 SHA-256。
UPDATE knowledge_chunk SET checksum = md5(content) WHERE checksum IS NULL;

ALTER TABLE knowledge_chunk ALTER COLUMN checksum SET NOT NULL;

-- 演示文档没有真实审批事件，以初始化时刻作为发布审计起点。
UPDATE knowledge_document
SET approved_at = CURRENT_TIMESTAMP,
    published_at = CURRENT_TIMESTAMP
WHERE status IN ('PUBLISHED', 'EXPIRED');

UPDATE knowledge_document
SET expired_at = CURRENT_TIMESTAMP
WHERE status = 'EXPIRED';

COMMENT ON TABLE customer IS '客户基础信息与登录认证数据';
COMMENT ON COLUMN customer.id IS '客户数据库主键';
COMMENT ON COLUMN customer.customer_no IS '对外使用的唯一客户编号';
COMMENT ON COLUMN customer.name IS '客户展示名称';
COMMENT ON COLUMN customer.password_hash IS 'BCrypt 密码摘要，不存储明文密码';
COMMENT ON COLUMN customer.mobile_mask IS '脱敏后的手机号码';
COMMENT ON COLUMN customer.status IS '客户账户状态，如 ACTIVE';
COMMENT ON COLUMN customer.created_at IS '客户记录创建时间';

COMMENT ON TABLE loan IS '客户贷款合同与当前结清试算基础数据';
COMMENT ON COLUMN loan.id IS '贷款数据库主键';
COMMENT ON COLUMN loan.loan_no IS '对外使用的唯一贷款编号';
COMMENT ON COLUMN loan.customer_id IS '贷款所属客户主键';
COMMENT ON COLUMN loan.principal IS '贷款合同初始本金，单位为元';
COMMENT ON COLUMN loan.annual_rate IS '合同年化利率，小数形式';
COMMENT ON COLUMN loan.term IS '贷款总期数';
COMMENT ON COLUMN loan.status IS '贷款当前状态，如 ACTIVE';
COMMENT ON COLUMN loan.version IS '贷款记录乐观锁版本号';
COMMENT ON COLUMN loan.remaining_principal IS '提前结清试算使用的剩余本金，单位为元';
COMMENT ON COLUMN loan.interest_accrued_through IS '利息已计提截止日期';

COMMENT ON TABLE repayment_plan IS '贷款分期还款计划';
COMMENT ON COLUMN repayment_plan.id IS '还款计划数据库主键';
COMMENT ON COLUMN repayment_plan.loan_id IS '还款计划所属贷款主键';
COMMENT ON COLUMN repayment_plan.period_no IS '贷款内的还款期次';
COMMENT ON COLUMN repayment_plan.due_date IS '本期计划还款日期';
COMMENT ON COLUMN repayment_plan.principal IS '本期应还本金，单位为元';
COMMENT ON COLUMN repayment_plan.interest IS '本期应还利息，单位为元';
COMMENT ON COLUMN repayment_plan.fee IS '本期应还费用，单位为元';
COMMENT ON COLUMN repayment_plan.paid_amount IS '本期累计实还金额，单位为元';
COMMENT ON COLUMN repayment_plan.status IS '还款计划状态，如 PENDING';
COMMENT ON CONSTRAINT uk_repayment_plan_period ON repayment_plan IS '同一贷款的还款期次不可重复';

COMMENT ON TABLE loan_application IS '贷款申请及审批进度';
COMMENT ON COLUMN loan_application.id IS '贷款申请数据库主键';
COMMENT ON COLUMN loan_application.application_no IS '对外使用的唯一申请编号';
COMMENT ON COLUMN loan_application.customer_id IS '申请所属客户主键';
COMMENT ON COLUMN loan_application.product_code IS '申请对应的贷款产品编码';
COMMENT ON COLUMN loan_application.status IS '申请当前审批状态';
COMMENT ON COLUMN loan_application.reason_code IS '审批状态对应的原因码';
COMMENT ON COLUMN loan_application.missing_documents IS '仍需客户补充的材料 JSON 数组';
COMMENT ON COLUMN loan_application.updated_at IS '申请状态最后更新时间';

COMMENT ON TABLE payment_record IS '贷款还款支付流水';
COMMENT ON COLUMN payment_record.id IS '支付流水数据库主键';
COMMENT ON COLUMN payment_record.payment_no IS '对外使用的唯一支付流水号';
COMMENT ON COLUMN payment_record.loan_id IS '支付流水关联的贷款主键';
COMMENT ON COLUMN payment_record.customer_id IS '支付流水所属客户主键，用于强制数据隔离';
COMMENT ON COLUMN payment_record.amount IS '本次支付金额，单位为元';
COMMENT ON COLUMN payment_record.channel IS '支付渠道编码，如 BANK_CARD';
COMMENT ON COLUMN payment_record.bank_card_last_four IS '扣款银行卡后四位，不存储完整卡号';
COMMENT ON COLUMN payment_record.status IS '支付处理状态，如 SUCCESS 或 PROCESSING';
COMMENT ON COLUMN payment_record.paid_at IS '支付发生或进入处理状态的时间';

COMMENT ON TABLE service_ticket IS '客户服务与高风险人工接管工单';
COMMENT ON COLUMN service_ticket.id IS '工单数据库主键';
COMMENT ON COLUMN service_ticket.ticket_no IS '对外使用的唯一工单编号';
COMMENT ON COLUMN service_ticket.customer_id IS '工单所属客户主键';
COMMENT ON COLUMN service_ticket.conversation_id IS '创建工单的对话会话标识';
COMMENT ON COLUMN service_ticket.related_loan_id IS '工单关联的贷款主键，可为空';
COMMENT ON COLUMN service_ticket.type IS '工单业务类型';
COMMENT ON COLUMN service_ticket.priority IS '工单处理优先级';
COMMENT ON COLUMN service_ticket.summary IS '经过空白规范化与长度限制的问题摘要';
COMMENT ON COLUMN service_ticket.status IS '工单当前处理状态';
COMMENT ON COLUMN service_ticket.idempotency_key IS '客户、会话、类型与关联资源组成的写入幂等键';
COMMENT ON COLUMN service_ticket.created_at IS '工单创建时间';

COMMENT ON TABLE chat_session IS '按客户隔离的客服对话会话';
COMMENT ON COLUMN chat_session.id IS '对话会话数据库主键';
COMMENT ON COLUMN chat_session.session_id IS '客户端使用的唯一会话标识';
COMMENT ON COLUMN chat_session.customer_id IS '会话所属客户主键';
COMMENT ON COLUMN chat_session.selected_loan_id IS '最近一次明确选择的贷款编号，仅作为短期会话槽位';
COMMENT ON COLUMN chat_session.selected_application_id IS '最近一次明确选择的贷款申请编号，仅作为短期会话槽位';
COMMENT ON COLUMN chat_session.selected_product_code IS '最近一次明确选择的产品编码，仅作为短期会话槽位';
COMMENT ON COLUMN chat_session.selected_region IS '最近一次明确选择的地区编码，仅作为短期会话槽位';
COMMENT ON COLUMN chat_session.created_at IS '会话创建时间';
COMMENT ON COLUMN chat_session.updated_at IS '会话最近消息时间';

COMMENT ON TABLE chat_message IS '经过脱敏和长度限制的客服对话消息';
COMMENT ON COLUMN chat_message.id IS '对话消息数据库主键';
COMMENT ON COLUMN chat_message.session_id IS '消息所属会话主键';
COMMENT ON COLUMN chat_message.message_id IS '客户端消息标识或服务端派生消息标识';
COMMENT ON COLUMN chat_message.role IS '消息角色：USER 或 ASSISTANT';
COMMENT ON COLUMN chat_message.content IS '脱敏并截断后的消息内容';
COMMENT ON COLUMN chat_message.intent IS 'Assistant 消息对应的最终意图';
COMMENT ON COLUMN chat_message.trace_id IS 'Assistant 消息对应的 Agent Trace 标识';
COMMENT ON COLUMN chat_message.created_at IS '消息创建时间';
COMMENT ON CONSTRAINT ck_chat_message_role ON chat_message IS '限制消息角色为 USER 或 ASSISTANT';
COMMENT ON CONSTRAINT uk_chat_message_identity ON chat_message IS '同一会话内相同消息标识和角色只允许写入一次';

COMMENT ON TABLE agent_trace IS 'Agent 节点级执行追踪与审计记录';
COMMENT ON COLUMN agent_trace.id IS 'Trace 记录数据库主键';
COMMENT ON COLUMN agent_trace.trace_id IS '单次 Agent 请求的全链路追踪标识';
COMMENT ON COLUMN agent_trace.session_id IS '当前对话会话标识';
COMMENT ON COLUMN agent_trace.customer_id IS 'Trace 所属客户主键，用于查询隔离';
COMMENT ON COLUMN agent_trace.node_sequence IS '节点在当前 Trace 内的单调递增顺序号';
COMMENT ON COLUMN agent_trace.node_name IS 'Agent 节点名称';
COMMENT ON COLUMN agent_trace.input_summary IS '已脱敏并截断的节点输入摘要';
COMMENT ON COLUMN agent_trace.output_summary IS '已脱敏并截断的节点输出摘要';
COMMENT ON COLUMN agent_trace.model_version IS '节点实际使用的模型版本，无模型调用时为空';
COMMENT ON COLUMN agent_trace.prompt_version IS '节点实际使用的提示词版本，无提示词时为空';
COMMENT ON COLUMN agent_trace.latency_ms IS '节点执行耗时，单位为毫秒';
COMMENT ON COLUMN agent_trace.status IS '节点执行状态，如 SUCCESS 或 FAILED';
COMMENT ON COLUMN agent_trace.created_at IS 'Trace 节点记录创建时间';
COMMENT ON CONSTRAINT uk_agent_trace_sequence ON agent_trace IS '同一 Trace 内的节点顺序不可重复';

COMMENT ON TABLE knowledge_document IS '带版本、有效期、访问范围和发布状态的知识文档';
COMMENT ON COLUMN knowledge_document.id IS '知识文档数据库主键';
COMMENT ON COLUMN knowledge_document.document_no IS '跨版本稳定的知识文档业务编号';
COMMENT ON COLUMN knowledge_document.title IS '知识文档标题';
COMMENT ON COLUMN knowledge_document.product_code IS '知识文档适用的产品编码';
COMMENT ON COLUMN knowledge_document.region IS '知识文档适用地区，ALL 表示全地区';
COMMENT ON COLUMN knowledge_document.version IS '知识文档版本号';
COMMENT ON COLUMN knowledge_document.audience IS '访问范围，如 PUBLIC、CUSTOMER 或 INTERNAL';
COMMENT ON COLUMN knowledge_document.status IS '发布状态：DRAFT、APPROVED、PUBLISHED 或 EXPIRED';
COMMENT ON COLUMN knowledge_document.effective_from IS '知识文档开始生效日期';
COMMENT ON COLUMN knowledge_document.effective_to IS '知识文档失效日期，长期有效时为空';
COMMENT ON COLUMN knowledge_document.reviewer IS '最近一次完成合规审核的审核人';
COMMENT ON COLUMN knowledge_document.checksum IS '原始文档内容校验和，用于幂等和版本防覆盖';
COMMENT ON COLUMN knowledge_document.approved_at IS '文档通过审核的时间';
COMMENT ON COLUMN knowledge_document.published_at IS '文档正式发布的时间';
COMMENT ON COLUMN knowledge_document.expired_at IS '文档下线失效的时间';
COMMENT ON CONSTRAINT uk_knowledge_document_version ON knowledge_document IS '同一文档业务编号下的版本号不可重复';
COMMENT ON CONSTRAINT ck_knowledge_document_status ON knowledge_document IS '限制知识文档只能处于受控发布状态';

COMMENT ON TABLE knowledge_chunk IS '用于全文与向量检索的知识文档切片';
COMMENT ON COLUMN knowledge_chunk.id IS '知识切片数据库主键';
COMMENT ON COLUMN knowledge_chunk.document_id IS '切片所属知识文档主键';
COMMENT ON COLUMN knowledge_chunk.chunk_no IS '文档内稳定且可重复生成的切片编号';
COMMENT ON COLUMN knowledge_chunk.section_path IS '切片在原文中的标题层级路径';
COMMENT ON COLUMN knowledge_chunk.content IS '用于回答客户问题的切片正文';
COMMENT ON COLUMN knowledge_chunk.search_text IS '包含标题、路径和受控关键词的全文检索文本';
COMMENT ON COLUMN knowledge_chunk.search_vector IS '由 search_text 自动生成的 PostgreSQL 全文检索向量';
COMMENT ON COLUMN knowledge_chunk.embedding IS '1024 维知识切片向量，未生成时为空';
COMMENT ON COLUMN knowledge_chunk.embedding_model IS '生成当前向量的 Embedding 模型名称';
COMMENT ON COLUMN knowledge_chunk.embedded_at IS '当前向量生成并写入数据库的时间';
COMMENT ON COLUMN knowledge_chunk.checksum IS '切片正文校验和';
COMMENT ON CONSTRAINT uk_knowledge_chunk_no ON knowledge_chunk IS '同一知识文档内的切片编号不可重复';

COMMENT ON INDEX idx_loan_customer IS '按客户查询贷款并实施资源归属校验';
COMMENT ON INDEX idx_repayment_plan_next IS '按贷款和到期日查询下一期待还计划';
COMMENT ON INDEX idx_loan_application_customer IS '按客户隔离查询贷款申请';
COMMENT ON INDEX idx_payment_record_loan_time IS '按贷款和支付时间倒序查询还款记录';
COMMENT ON INDEX idx_service_ticket_customer IS '按客户和创建时间倒序查询工单';
COMMENT ON INDEX idx_chat_session_customer IS '按客户和最近消息时间查询会话';
COMMENT ON INDEX idx_chat_message_session IS '按会话和时间顺序查询消息';
COMMENT ON INDEX idx_agent_trace_customer IS '按客户和创建时间查询 Agent Trace';
COMMENT ON INDEX idx_knowledge_chunk_fts IS '知识切片全文检索 GIN 索引';
COMMENT ON INDEX idx_knowledge_chunk_trgm IS '知识检索文本模糊匹配 trigram 索引';
COMMENT ON INDEX idx_knowledge_chunk_embedding_hnsw IS '知识切片余弦距离 HNSW 向量索引，仅索引非空向量';
