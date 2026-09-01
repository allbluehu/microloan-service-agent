CREATE TABLE service_ticket (
    id BIGSERIAL PRIMARY KEY,
    ticket_no VARCHAR(40) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    conversation_id VARCHAR(64) NOT NULL,
    related_loan_id BIGINT REFERENCES loan(id),
    type VARCHAR(40) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_service_ticket_customer ON service_ticket(customer_id, created_at DESC);
