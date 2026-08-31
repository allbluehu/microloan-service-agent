CREATE TABLE customer (
    id BIGSERIAL PRIMARY KEY,
    customer_no VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    mobile_mask VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE loan (
    id BIGSERIAL PRIMARY KEY,
    loan_no VARCHAR(32) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    principal NUMERIC(18, 2) NOT NULL,
    annual_rate NUMERIC(10, 6) NOT NULL,
    term INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE repayment_plan (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL REFERENCES loan(id),
    period_no INTEGER NOT NULL,
    due_date DATE NOT NULL,
    principal NUMERIC(18, 2) NOT NULL,
    interest NUMERIC(18, 2) NOT NULL,
    fee NUMERIC(18, 2) NOT NULL DEFAULT 0,
    paid_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uk_repayment_plan_period UNIQUE (loan_id, period_no)
);

CREATE INDEX idx_loan_customer ON loan(customer_id);
CREATE INDEX idx_repayment_plan_next ON repayment_plan(loan_id, due_date);
