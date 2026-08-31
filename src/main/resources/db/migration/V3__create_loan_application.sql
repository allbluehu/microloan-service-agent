CREATE TABLE loan_application (
    id BIGSERIAL PRIMARY KEY,
    application_no VARCHAR(32) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    product_code VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason_code VARCHAR(50),
    missing_documents JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
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
