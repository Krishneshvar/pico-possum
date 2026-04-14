-- V18__remove_tax_module.sql

-- Disable foreign keys temporarily to allow restructuring
PRAGMA foreign_keys = OFF;

-- Drop dependent indexes first
DROP INDEX IF EXISTS idx_products_tax_category_id;
DROP INDEX IF EXISTS idx_customers_is_tax_exempt;

-- 1. Recreate products table without tax_category_id
CREATE TABLE products_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  description TEXT,
  category_id INTEGER,
  status TEXT CHECK(status IN ('active','inactive','discontinued')) DEFAULT 'active',
  image_path TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  sku TEXT,
  mrp NUMERIC(10,2) DEFAULT 0,
  cost_price NUMERIC(10,2) DEFAULT 0,
  stock_alert_cap INTEGER DEFAULT 10,
  FOREIGN KEY (category_id) REFERENCES categories(id)
);

INSERT INTO products_new (id, name, description, category_id, status, image_path, created_at, updated_at, deleted_at, sku, mrp, cost_price, stock_alert_cap)
SELECT id, name, description, category_id, status, image_path, created_at, updated_at, deleted_at, sku, mrp, cost_price, stock_alert_cap
FROM products;

DROP TABLE products;
ALTER TABLE products_new RENAME TO products;

-- Recreate indexes for products
CREATE INDEX IF NOT EXISTS idx_products_category_id ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);
CREATE INDEX IF NOT EXISTS idx_products_status ON products(status);
CREATE INDEX IF NOT EXISTS idx_products_deleted_at ON products(deleted_at);
CREATE INDEX IF NOT EXISTS idx_products_status_deleted ON products(status, deleted_at);
CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku) WHERE sku IS NOT NULL;

-- 2. Recreate sales without total_tax
CREATE TABLE sales_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  invoice_number TEXT NOT NULL UNIQUE,
  sale_date DATETIME DEFAULT CURRENT_TIMESTAMP,
  total_amount NUMERIC(10,2) NOT NULL,
  paid_amount NUMERIC(10,2) NOT NULL,
  discount NUMERIC(10,2) DEFAULT 0,
  status TEXT CHECK(status IN ('draft','paid','partially_paid','cancelled','refunded','partially_refunded')) NOT NULL,
  fulfillment_status TEXT CHECK(fulfillment_status IN ('pending','fulfilled','cancelled')) NOT NULL DEFAULT 'pending',
  customer_id INTEGER,
  user_id INTEGER NOT NULL,
  FOREIGN KEY (customer_id) REFERENCES customers(id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

INSERT INTO sales_new (id, invoice_number, sale_date, total_amount, paid_amount, discount, status, fulfillment_status, customer_id, user_id)
SELECT id, invoice_number, sale_date, total_amount, paid_amount, discount, status, fulfillment_status, customer_id, user_id
FROM sales;

DROP TABLE sales;
ALTER TABLE sales_new RENAME TO sales;

-- Recreate indexes for sales
CREATE INDEX IF NOT EXISTS idx_sales_customer_id ON sales(customer_id);
CREATE INDEX IF NOT EXISTS idx_sales_user_id ON sales(user_id);
CREATE INDEX IF NOT EXISTS idx_sales_date ON sales(sale_date);
CREATE INDEX IF NOT EXISTS idx_sales_status ON sales(status);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_number ON sales(invoice_number);
CREATE INDEX IF NOT EXISTS idx_sales_fulfillment_status ON sales(fulfillment_status);
CREATE INDEX IF NOT EXISTS idx_sales_status_date ON sales(status, sale_date DESC);

-- 3. Recreate sale_items without tax columns
CREATE TABLE sale_items_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sale_id INTEGER NOT NULL,
  product_id INTEGER NOT NULL,
  quantity INTEGER NOT NULL,
  price_per_unit NUMERIC(10,2) NOT NULL,
  cost_per_unit NUMERIC(10,2) NOT NULL,
  discount_amount NUMERIC(10,2) DEFAULT 0,
  FOREIGN KEY (sale_id) REFERENCES sales(id),
  FOREIGN KEY (product_id) REFERENCES products(id)
);

INSERT INTO sale_items_new (id, sale_id, product_id, quantity, price_per_unit, cost_per_unit, discount_amount)
SELECT id, sale_id, product_id, quantity, price_per_unit, cost_per_unit, discount_amount
FROM sale_items;

DROP TABLE sale_items;
ALTER TABLE sale_items_new RENAME TO sale_items;

-- Recreate indexes for sale_items
CREATE INDEX IF NOT EXISTS idx_sale_items_sale_id ON sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_product_sale ON sale_items(product_id, sale_id);

-- 4. Recreate return_items to fix point-to-old_sale_items issue from V16
CREATE TABLE return_items_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  return_id INTEGER NOT NULL,
  sale_item_id INTEGER NOT NULL,
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  refund_amount NUMERIC(10,2) NOT NULL CHECK (refund_amount >= 0),
  FOREIGN KEY (return_id) REFERENCES returns(id) ON DELETE CASCADE,
  FOREIGN KEY (sale_item_id) REFERENCES sale_items(id)
);

INSERT INTO return_items_new (id, return_id, sale_item_id, quantity, refund_amount)
SELECT id, return_id, sale_item_id, quantity, refund_amount
FROM return_items;

DROP TABLE return_items;
ALTER TABLE return_items_new RENAME TO return_items;

-- Recreate indexes for return_items
CREATE INDEX IF NOT EXISTS idx_return_items_return_id ON return_items(return_id);
CREATE INDEX IF NOT EXISTS idx_return_items_sale_item_id ON return_items(sale_item_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_return_items_return_sale_item ON return_items(return_id, sale_item_id);

-- 5. Recreate customers without is_tax_exempt
CREATE TABLE customers_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL CHECK (length(trim(name)) > 0),
  phone TEXT,
  email TEXT,
  address TEXT,
  customer_type TEXT DEFAULT 'retail',
  created_at TEXT DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
  deleted_at TEXT
) STRICT;

INSERT INTO customers_new (id, name, phone, email, address, customer_type, created_at, updated_at, deleted_at)
SELECT id, name, phone, email, address, customer_type, created_at, updated_at, deleted_at
FROM customers;

DROP TABLE customers;
ALTER TABLE customers_new RENAME TO customers;

-- Recreate indexes for customers
CREATE INDEX IF NOT EXISTS idx_customers_name ON customers(name);
CREATE INDEX IF NOT EXISTS idx_customers_phone ON customers(phone);
CREATE INDEX IF NOT EXISTS idx_customers_email ON customers(email);
CREATE INDEX IF NOT EXISTS idx_customers_created_at ON customers(created_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_phone_unique_active ON customers(phone) WHERE phone IS NOT NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_email_unique_active ON customers(email) WHERE email IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_customers_customer_type ON customers(customer_type);

-- 6. Drop tax-related tables
DROP TABLE IF EXISTS tax_exemptions;
DROP TABLE IF EXISTS tax_profiles;
DROP TABLE IF EXISTS tax_rules;
DROP TABLE IF EXISTS tax_categories;
DROP TABLE IF EXISTS tax_calculation_log;

-- Re-enable foreign keys
PRAGMA foreign_keys = ON;
