ALTER TABLE customers ADD COLUMN customer_type TEXT DEFAULT 'retail';
ALTER TABLE customers ADD COLUMN is_tax_exempt INTEGER NOT NULL DEFAULT 0 CHECK(is_tax_exempt IN (0,1));
