-- V8: Store legacy bill-level sales imported from "serieswise" CSV exports.
-- These records are summary-only (no line items), but are reusable in
-- bill history, transactions, and reporting/analytics aggregates.

CREATE TABLE IF NOT EXISTS legacy_sales (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  invoice_number TEXT NOT NULL UNIQUE,
  sale_date DATETIME NOT NULL,
  customer_code TEXT,
  customer_name TEXT,
  net_amount NUMERIC(10,2) NOT NULL CHECK(net_amount >= 0),
  source_file TEXT,
  imported_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_legacy_sales_sale_date ON legacy_sales(sale_date);
CREATE INDEX IF NOT EXISTS idx_legacy_sales_customer_name ON legacy_sales(customer_name);
CREATE INDEX IF NOT EXISTS idx_legacy_sales_invoice_number ON legacy_sales(invoice_number);
