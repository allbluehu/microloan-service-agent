CREATE TABLE ticket_action (
    id BIGSERIAL PRIMARY KEY,
    ticket_no VARCHAR(64) NOT NULL REFERENCES service_ticket(ticket_no),
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    note VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ticket_action_transition CHECK (
        (from_status = 'OPEN' AND to_status = 'IN_PROGRESS') OR
        (from_status = 'IN_PROGRESS' AND to_status = 'RESOLVED')
    )
);
COMMENT ON TABLE ticket_action IS '坐席工单状态变更审计，操作者为独立密钥认证的演示坐席';
COMMENT ON COLUMN ticket_action.id IS '审计主键';
COMMENT ON COLUMN ticket_action.ticket_no IS '关联工单编号';
COMMENT ON COLUMN ticket_action.from_status IS '变更前状态';
COMMENT ON COLUMN ticket_action.to_status IS '变更后状态';
COMMENT ON COLUMN ticket_action.note IS '脱敏处理说明';
COMMENT ON COLUMN ticket_action.created_at IS '操作时间';

CREATE INDEX idx_ticket_action_ticket ON ticket_action(ticket_no, created_at);
COMMENT ON INDEX idx_ticket_action_ticket IS '按工单读取处理审计';
