-- V9: Add payment method attribution for legacy sales so reports/filters can
-- classify them like regular transactions.

ALTER TABLE legacy_sales ADD COLUMN payment_method_id INTEGER;
ALTER TABLE legacy_sales ADD COLUMN payment_method_name TEXT;

-- Backfill from invoice prefixes:
-- C* and X* => Cash
-- K* => Debit Card
UPDATE legacy_sales
SET payment_method_name = CASE
    WHEN UPPER(SUBSTR(invoice_number, 1, 1)) IN ('C', 'X') THEN 'Cash'
    WHEN UPPER(SUBSTR(invoice_number, 1, 1)) = 'K' THEN 'Debit Card'
    ELSE COALESCE(payment_method_name, 'Legacy Import')
END
WHERE payment_method_name IS NULL OR TRIM(payment_method_name) = '';

UPDATE legacy_sales
SET payment_method_id = CASE
    WHEN payment_method_name = 'Cash' THEN (
        SELECT id FROM payment_methods
        WHERE LOWER(name) = 'cash' OR UPPER(COALESCE(code, '')) = 'CH'
        LIMIT 1
    )
    WHEN payment_method_name = 'Debit Card' THEN (
        SELECT id FROM payment_methods
        WHERE LOWER(name) = 'debit card' OR UPPER(COALESCE(code, '')) = 'DC'
        LIMIT 1
    )
    ELSE payment_method_id
END
WHERE payment_method_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_legacy_sales_payment_method_id ON legacy_sales(payment_method_id);
CREATE INDEX IF NOT EXISTS idx_legacy_sales_payment_method_name ON legacy_sales(payment_method_name);
