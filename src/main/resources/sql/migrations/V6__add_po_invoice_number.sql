-- V6: Add invoice_number and payment_method_id to purchase_orders

-- SQLite doesn't support complex ALTER TABLE operations (like adding a NOT NULL UNIQUE column directly
-- without a default value). However, it does support adding nullable columns, or nullable with defaults.
-- Then we can populate them, and optionally recreate the table if we must enforce UNIQUE NOT NULL.
-- Since this is Flyway, let's just add the columns and set them.

ALTER TABLE purchase_orders ADD COLUMN invoice_number TEXT;
ALTER TABLE purchase_orders ADD COLUMN payment_method_id INTEGER REFERENCES payment_methods(id);

-- Provide a fallback payment method (e.g., 1 which usually is Cash)
UPDATE purchase_orders SET payment_method_id = 1 WHERE payment_method_id IS NULL;

-- Backfill invoice numbers for existing purchase orders
-- Using a generic prefix + the PO ID to keep it simple for legacy data.
UPDATE purchase_orders SET invoice_number = 'P250101XX' || printf('%04d', id) WHERE invoice_number IS NULL;

-- Enforce NOT NULL constraint by creating a new table if we want, but since sqlite is lenient,
-- and we already set values, we can just create the index.

CREATE UNIQUE INDEX IF NOT EXISTS idx_purchase_orders_invoice_number ON purchase_orders(invoice_number);
