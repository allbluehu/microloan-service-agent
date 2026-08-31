-- The {noop} credential is restricted to local demo data and must never be used in a real deployment.
INSERT INTO customer (id, customer_no, name, password_hash, mobile_mask, status)
VALUES
    (10001, 'C10001', '张三', '{noop}Demo123!', '138****0001', 'ACTIVE'),
    (10002, 'C10002', '李四', '{noop}Demo123!', '138****0002', 'ACTIVE');

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
