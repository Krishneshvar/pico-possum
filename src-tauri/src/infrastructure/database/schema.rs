pub const INIT_SCHEMA: &str = r#"
PRAGMA foreign_keys = ON;

-- 1. Categories
CREATE TABLE IF NOT EXISTS categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    parent_id TEXT REFERENCES categories(id) ON DELETE SET NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 2. Products
CREATE TABLE IF NOT EXISTS products (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    sku TEXT UNIQUE,
    category_id TEXT REFERENCES categories(id) ON DELETE SET NULL,
    mrp_paise INTEGER NOT NULL CHECK(mrp_paise >= 0),
    cost_price_paise INTEGER NOT NULL DEFAULT 0 CHECK(cost_price_paise >= 0),
    tax_rate REAL NOT NULL DEFAULT 0.0,
    current_stock INTEGER NOT NULL DEFAULT 0,
    min_stock_alert INTEGER NOT NULL DEFAULT 5,
    status TEXT NOT NULL CHECK(status IN ('active', 'inactive', 'discontinued')) DEFAULT 'active',
    is_deleted BOOLEAN NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 3. Customers
CREATE TABLE IF NOT EXISTS customers (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT,
    address TEXT,
    is_deleted BOOLEAN NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 4. Sales Ledger
CREATE TABLE IF NOT EXISTS sales (
    id TEXT PRIMARY KEY,
    invoice_number TEXT NOT NULL UNIQUE,
    customer_id TEXT REFERENCES customers(id) ON DELETE SET NULL,
    customer_name TEXT,
    customer_phone TEXT,
    biller_name TEXT,
    subtotal_paise INTEGER NOT NULL CHECK(subtotal_paise >= 0),
    discount_paise INTEGER NOT NULL DEFAULT 0 CHECK(discount_paise >= 0),
    tax_paise INTEGER NOT NULL DEFAULT 0 CHECK(tax_paise >= 0),
    total_paise INTEGER NOT NULL CHECK(total_paise >= 0),
    paid_paise INTEGER NOT NULL DEFAULT 0 CHECK(paid_paise >= 0),
    payment_method TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('PAID', 'PARTIALLY_PAID', 'CANCELLED', 'REFUNDED')) DEFAULT 'PAID',
    fulfillment_status TEXT NOT NULL DEFAULT 'FULFILLED',
    notes TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 5. Sale Line Items
CREATE TABLE IF NOT EXISTS sale_items (
    id TEXT PRIMARY KEY,
    sale_id TEXT NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    product_id TEXT NOT NULL REFERENCES products(id),
    product_name TEXT NOT NULL,
    product_sku TEXT,
    quantity INTEGER NOT NULL CHECK(quantity > 0),
    unit_price_paise INTEGER NOT NULL CHECK(unit_price_paise >= 0),
    cost_price_paise INTEGER NOT NULL DEFAULT 0 CHECK(cost_price_paise >= 0),
    discount_paise INTEGER NOT NULL DEFAULT 0 CHECK(discount_paise >= 0),
    tax_rate REAL NOT NULL DEFAULT 0.0,
    tax_paise INTEGER NOT NULL DEFAULT 0 CHECK(tax_paise >= 0),
    total_paise INTEGER NOT NULL CHECK(total_paise >= 0)
);

-- 6. Stock Movements (Ledger)
CREATE TABLE IF NOT EXISTS stock_movements (
    id TEXT PRIMARY KEY,
    product_id TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity_change INTEGER NOT NULL,
    movement_type TEXT NOT NULL,
    reference_id TEXT,
    note TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 7. Audit Trail
CREATE TABLE IF NOT EXISTS audit_logs (
    id TEXT PRIMARY KEY,
    event_type TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 8. Persistent App & Bill Settings
CREATE TABLE IF NOT EXISTS app_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 9. Invoice Sequence Tracker
CREATE TABLE IF NOT EXISTS invoice_sequences (
    payment_code TEXT PRIMARY KEY,
    last_sequence INTEGER NOT NULL DEFAULT 0
);

-- 10. Persistent POS Draft Tabs (up to 9 open bills)
CREATE TABLE IF NOT EXISTS pos_open_bills (
    bill_index INTEGER PRIMARY KEY,
    payload TEXT NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku);
CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);
CREATE INDEX IF NOT EXISTS idx_sales_invoice ON sales(invoice_number);
CREATE INDEX IF NOT EXISTS idx_sales_date ON sales(created_at);
CREATE INDEX IF NOT EXISTS idx_stock_movements_prod ON stock_movements(product_id, created_at);
CREATE INDEX IF NOT EXISTS idx_sale_items_sale ON sale_items(sale_id);
"#;
