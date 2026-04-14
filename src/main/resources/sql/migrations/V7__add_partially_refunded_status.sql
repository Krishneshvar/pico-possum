-- V7: Add 'partially_refunded' status to the sales table status CHECK constraint.
-- SQLite requires the table recreation pattern to modify CHECK constraints.

-- We rely on Flyway's own transaction management. 
-- However, dropping/renaming tables with foreign keys requires PRAGMA foreign_keys=OFF.
PRAGMA foreign_keys=OFF;

-- 1. Create a temporary table with the new schema (including 'partially_refunded').
CREATE TABLE sales_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  invoice_number TEXT NOT NULL UNIQUE,
  sale_date DATETIME DEFAULT CURRENT_TIMESTAMP,
  total_amount NUMERIC(10,2) NOT NULL,
  paid_amount NUMERIC(10,2) NOT NULL,
  discount NUMERIC(10,2) DEFAULT 0,
  total_tax NUMERIC(10,2) DEFAULT 0,
  status TEXT CHECK(status IN ('draft','paid','partially_paid','cancelled','refunded','partially_refunded')) NOT NULL,
  fulfillment_status TEXT CHECK(fulfillment_status IN ('pending','fulfilled','cancelled')) NOT NULL DEFAULT 'pending',
  customer_id INTEGER,
  user_id INTEGER NOT NULL,
  FOREIGN KEY (customer_id) REFERENCES customers(id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 2. Copy the data from the old sales table to the new one.
INSERT INTO sales_new (
    id, invoice_number, sale_date, total_amount, paid_amount, discount, 
    total_tax, status, fulfillment_status, customer_id, user_id
)
SELECT 
    id, invoice_number, sale_date, total_amount, paid_amount, discount, 
    total_tax, status, fulfillment_status, customer_id, user_id
FROM sales;

-- 3. Drop the old sales table.
DROP TABLE sales;

-- 4. Rename the new table to 'sales'.
ALTER TABLE sales_new RENAME TO sales;

-- 5. Recreate all original indexes for the sales table.
CREATE INDEX idx_sales_customer_id ON sales(customer_id);
CREATE INDEX idx_sales_user_id ON sales(user_id);
CREATE INDEX idx_sales_date ON sales(sale_date);
CREATE INDEX idx_sales_status ON sales(status);
CREATE INDEX idx_sales_invoice_number ON sales(invoice_number);
CREATE INDEX idx_sales_fulfillment_status ON sales(fulfillment_status);
CREATE INDEX idx_sales_status_date ON sales(status, sale_date DESC);

-- Restore foreign key enforcement.
PRAGMA foreign_keys=ON;
