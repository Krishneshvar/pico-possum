-- Update payment method codes and names to new standards
UPDATE payment_methods SET name = 'Cash', code = 'CH' WHERE id = 1;
UPDATE payment_methods SET name = 'Card', code = 'CP' WHERE id = 2;
UPDATE payment_methods SET name = 'UPI', code = 'UP' WHERE id = 3;
UPDATE payment_methods SET name = 'Gift Card', code = 'GC' WHERE id = 4;

-- Optionally update existing invoice numbers if they contain the old codes
-- This is a best-effort update for consistency
UPDATE sales SET invoice_number = REPLACE(invoice_number, 'CASH', 'CH') WHERE invoice_number LIKE '%CASH%';
UPDATE sales SET invoice_number = REPLACE(invoice_number, 'CARD', 'CP') WHERE invoice_number LIKE '%CARD%';
UPDATE sales SET invoice_number = REPLACE(invoice_number, 'UPI', 'UP') WHERE invoice_number LIKE '%UPI%';
UPDATE sales SET invoice_number = REPLACE(invoice_number, 'CREDIT', 'GC') WHERE invoice_number LIKE '%CREDIT%';

-- Also return invoice numbers are joined from sales, so they will reflect the changes automatically.

