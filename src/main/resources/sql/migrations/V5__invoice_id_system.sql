-- V5: Invoice ID System — Data setup & sequence counters
-- Part 2 of 2: Data migration for the PTYYMMDDXXXX invoice ID system.
-- Assumes the `code` column was added in V3.

-- Step 1: Robustly rename/merge 'Card' into 'Debit Card'.
-- Ensure 'Debit Card' exists first.
INSERT OR IGNORE INTO payment_methods (name) VALUES ('Debit Card');
-- Reassign any transactions already pointing to 'Card'.
UPDATE OR IGNORE transactions 
SET payment_method_id = (SELECT id FROM payment_methods WHERE name = 'Debit Card') 
WHERE payment_method_id = (SELECT id FROM payment_methods WHERE name = 'Card');
-- Clear out the redundant 'Card' entry.
DELETE FROM payment_methods WHERE name = 'Card';

-- Step 2: Ensure all standard payment methods exist.
INSERT OR IGNORE INTO payment_methods (name) VALUES ('Cash');
INSERT OR IGNORE INTO payment_methods (name) VALUES ('Debit Card');
INSERT OR IGNORE INTO payment_methods (name) VALUES ('Credit Card');
INSERT OR IGNORE INTO payment_methods (name) VALUES ('Gift Card');
INSERT OR IGNORE INTO payment_methods (name) VALUES ('UPI');

-- Step 3: Backfill known payment method codes.
UPDATE payment_methods SET code = 'CH' WHERE name = 'Cash'        AND (code IS NULL OR code = '');
UPDATE payment_methods SET code = 'DC' WHERE name = 'Debit Card'  AND (code IS NULL OR code = '');
UPDATE payment_methods SET code = 'CC' WHERE name = 'Credit Card' AND (code IS NULL OR code = '');
UPDATE payment_methods SET code = 'GC' WHERE name = 'Gift Card'   AND (code IS NULL OR code = '');
UPDATE payment_methods SET code = 'UP' WHERE name = 'UPI'         AND (code IS NULL OR code = '');

-- Fallback: assign a code for any other method from the first 2 uppercase chars.
UPDATE payment_methods
SET code = UPPER(SUBSTR(REPLACE(name, ' ', ''), 1, 2))
WHERE name IS NOT NULL AND (code IS NULL OR code = '');

-- Step 4: Create the invoice_sequences table.
-- Tracks a global, monotonically increasing sequence per payment type (never resets).
CREATE TABLE IF NOT EXISTS invoice_sequences (
    payment_type_code TEXT PRIMARY KEY,
    last_sequence     INTEGER NOT NULL DEFAULT 0
);

-- Step 5: Seed sequence counters from existing payment methods (start at 0 → first ID will be 0001).
INSERT OR IGNORE INTO invoice_sequences (payment_type_code, last_sequence)
SELECT DISTINCT code, 0 FROM payment_methods WHERE code IS NOT NULL;
