ALTER TABLE loan
    ADD COLUMN remaining_principal NUMERIC(18, 2),
    ADD COLUMN interest_accrued_through DATE;

UPDATE loan
SET remaining_principal = CASE loan_no
        WHEN 'L10001' THEN 8200.00
        WHEN 'L99999' THEN 5600.00
        END,
    interest_accrued_through = DATE '2026-08-14';

ALTER TABLE loan
    ALTER COLUMN remaining_principal SET NOT NULL,
    ALTER COLUMN interest_accrued_through SET NOT NULL;
