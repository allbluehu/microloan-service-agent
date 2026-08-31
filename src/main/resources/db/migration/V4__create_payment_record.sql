CREATE TABLE payment_record (
    id BIGSERIAL PRIMARY KEY,
    payment_no VARCHAR(40) NOT NULL UNIQUE,
    loan_id BIGINT NOT NULL REFERENCES loan(id),
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    amount NUMERIC(18, 2) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    bank_card_last_four CHAR(4),
    status VARCHAR(20) NOT NULL,
    paid_at TIMESTAMPTZ NOT NULL
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
