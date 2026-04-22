-- Add tax support to sales and sale_items
ALTER TABLE sales ADD COLUMN tax_amount NUMERIC(10,2) DEFAULT 0;

ALTER TABLE sale_items ADD COLUMN tax_rate NUMERIC(10,2) DEFAULT 0;
ALTER TABLE sale_items ADD COLUMN tax_amount NUMERIC(10,2) DEFAULT 0;
