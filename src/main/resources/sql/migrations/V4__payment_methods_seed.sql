-- Essential payment methods seed data
INSERT OR IGNORE INTO payment_methods (name) VALUES 
('Cash'), 
('Debit Card'), 
('Credit Card'), 
('Gift Card'), 
('UPI');

-- Update codes (if column exists)
-- This is handled in V5 as well, so we can be conservative here or just do it.
UPDATE payment_methods SET code = 'CH' WHERE name = 'Cash' AND (code IS NULL OR code = '');
UPDATE payment_methods SET code = 'DC' WHERE name = 'Debit Card' AND (code IS NULL OR code = '');
UPDATE payment_methods SET code = 'CC' WHERE name = 'Credit Card' AND (code IS NULL OR code = '');
UPDATE payment_methods SET code = 'GC' WHERE name = 'Gift Card' AND (code IS NULL OR code = '');
UPDATE payment_methods SET code = 'UP' WHERE name = 'UPI' AND (code IS NULL OR code = '');
