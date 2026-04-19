-- Final erasure of the redundant Transactions module and its table.
-- Internalizing payment data into Sales and Returns tables for a cleaner "Sale-Centric" schema.

PRAGMA foreign_keys=OFF;

-- 1. Update Sales table to include payment method
ALTER TABLE sales ADD COLUMN payment_method_id INTEGER REFERENCES payment_methods(id);

-- 2. Update Returns table to include refund tracing
ALTER TABLE returns ADD COLUMN refund_amount NUMERIC(10,2) DEFAULT 0;
ALTER TABLE returns ADD COLUMN payment_method_id INTEGER REFERENCES payment_methods(id);

-- 3. Migrate existing data from transactions table
-- Migration for Sales payments
UPDATE sales 
SET payment_method_id = (
    SELECT payment_method_id 
    FROM transactions 
    WHERE transactions.sale_id = sales.id 
      AND transactions.type = 'payment' 
    LIMIT 1
);

-- Migration for Returns refunds
UPDATE returns 
SET refund_amount = (
    SELECT ABS(amount) 
    FROM transactions 
    WHERE transactions.sale_id = returns.sale_id 
      AND transactions.type = 'refund' 
    LIMIT 1
),
payment_method_id = (
    SELECT payment_method_id 
    FROM transactions 
    WHERE transactions.sale_id = returns.sale_id 
      AND transactions.type = 'refund' 
    LIMIT 1
);

-- 4. Remove Transaction Index that references the table
DROP INDEX IF EXISTS idx_transactions_sale;
DROP INDEX IF EXISTS idx_transactions_date;

-- 5. Final Eradication of the Transactions table
DROP TABLE transactions;

PRAGMA foreign_keys=ON;
