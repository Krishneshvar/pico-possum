-- V5: Standardize Invoice IDs for Sales and Returns
-- Adds the invoice_id column and populates it with the standard format: {TYPE}{YY}{METHOD}{NUMBER}

PRAGMA foreign_keys=OFF;

-- 1. Add invoice_id to sales
ALTER TABLE sales ADD COLUMN invoice_id TEXT;
CREATE UNIQUE INDEX idx_sales_invoice_id ON sales(invoice_id);

-- 2. Add invoice_id to returns
ALTER TABLE returns ADD COLUMN invoice_id TEXT;
CREATE UNIQUE INDEX idx_returns_invoice_id ON returns(invoice_id);

-- 3. Populate sales.invoice_id
-- We use the year from sale_date and the code from payment_methods
UPDATE sales
SET invoice_id = 'S' || strftime('%y', sale_date) || 
    COALESCE((SELECT code FROM payment_methods WHERE id = sales.payment_method_id), 'XX') || 
    printf('%07d', id);

-- 4. Populate returns.invoice_id
-- We use the year from created_at and the code from payment_methods
UPDATE returns
SET invoice_id = 'R' || strftime('%y', created_at) || 
    COALESCE((SELECT code FROM payment_methods WHERE id = returns.payment_method_id), 'XX') || 
    printf('%07d', id);

PRAGMA foreign_keys=ON;
