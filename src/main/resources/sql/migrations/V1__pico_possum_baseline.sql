-- Pico Possum baseline schema consolidation (V1__pico_possum_baseline.sql)

-- 1. Metadata & Settings
CREATE TABLE app_metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- 2. Security & Users
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    is_active INTEGER DEFAULT 1 CHECK(is_active IN (0,1)),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME
);

CREATE TABLE sessions (
    id TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL,
    token TEXT NOT NULL UNIQUE,
    expires_at INTEGER NOT NULL,
    data TEXT,
    ip_address TEXT,
    user_agent TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_activity_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 3. People
CREATE TABLE customers (
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

-- 4. Products & Inventory
CREATE TABLE categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    parent_id INTEGER,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME,
    FOREIGN KEY (parent_id) REFERENCES categories(id)
);

CREATE TABLE products (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT,
    category_id INTEGER,
    status TEXT CHECK(status IN ('active','inactive','discontinued')) DEFAULT 'active',
    image_path TEXT,
    sku TEXT,
    mrp NUMERIC(10,2) DEFAULT 0,
    cost_price NUMERIC(10,2) DEFAULT 0,
    stock_alert_cap INTEGER DEFAULT 10,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE TABLE inventory_lots (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id INTEGER NOT NULL,
    batch_number TEXT,
    manufactured_date TEXT,
    expiry_date TEXT,
    quantity INTEGER NOT NULL DEFAULT 0,
    unit_cost NUMERIC NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE TABLE inventory_adjustments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id INTEGER NOT NULL,
    lot_id INTEGER,
    quantity_change INTEGER NOT NULL,
    reason TEXT CHECK(reason IN ('sale','return','confirm_receive','spoilage','damage','theft','correction','product_deleted')) NOT NULL,
    reference_type TEXT,
    reference_id INTEGER,
    adjusted_by INTEGER NOT NULL,
    notes TEXT,
    adjusted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (lot_id) REFERENCES inventory_lots(id) ON DELETE SET NULL,
    FOREIGN KEY (adjusted_by) REFERENCES users(id)
);

CREATE TABLE product_stock_cache (
    product_id INTEGER PRIMARY KEY,
    current_stock INTEGER NOT NULL DEFAULT 0,
    last_updated DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- 5. Sales & Commercial
CREATE TABLE sales (
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

CREATE TABLE sale_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    price_per_unit NUMERIC(10,2) NOT NULL,
    cost_per_unit NUMERIC(10,2) NOT NULL,
    discount_amount NUMERIC(10,2) DEFAULT 0,
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE legacy_sales (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_number TEXT NOT NULL UNIQUE,
    sale_date DATETIME NOT NULL,
    customer_code TEXT,
    customer_name TEXT,
    net_amount NUMERIC(10,2) NOT NULL CHECK(net_amount >= 0),
    source_file TEXT,
    payment_method_id INTEGER,
    payment_method_name TEXT,
    imported_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE returns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    reason TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sale_id) REFERENCES sales(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE return_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    return_id INTEGER NOT NULL,
    sale_item_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    refund_amount NUMERIC(10,2) NOT NULL CHECK (refund_amount >= 0),
    FOREIGN KEY (return_id) REFERENCES returns(id) ON DELETE CASCADE,
    FOREIGN KEY (sale_item_id) REFERENCES sale_items(id) ON DELETE CASCADE
);

-- 6. Payments
CREATE TABLE payment_methods (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    code TEXT,
    is_active INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0,1))
);

CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id INTEGER,
    amount NUMERIC NOT NULL,
    type TEXT NOT NULL,
    payment_method_id INTEGER,
    status TEXT NOT NULL DEFAULT 'completed',
    transaction_date TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
    FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id)
);

-- 7. Analytics & Reports
CREATE TABLE product_flow (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id INTEGER NOT NULL,
    event_type TEXT CHECK(event_type IN ('sale','return','adjustment')) NOT NULL,
    quantity INTEGER NOT NULL,
    reference_type TEXT,
    reference_id INTEGER,
    event_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE TABLE sales_reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    report_type TEXT CHECK(report_type IN ('daily','monthly','yearly')) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_sales NUMERIC(10,2) NOT NULL,
    total_discount NUMERIC(10,2) NOT NULL,
    total_transactions INTEGER NOT NULL,
    generated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 8. Audit & Performance
CREATE TABLE audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    action TEXT NOT NULL,
    table_name TEXT,
    row_id INTEGER,
    old_data TEXT,
    new_data TEXT,
    event_details TEXT,
    ip_address TEXT,
    user_agent TEXT,
    severity TEXT DEFAULT 'info',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE query_performance_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    query_name TEXT NOT NULL,
    execution_time_ms INTEGER NOT NULL,
    row_count INTEGER,
    executed_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 9. POS UI State
CREATE TABLE drafts (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    payload TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE pos_open_bills (
    bill_index INTEGER PRIMARY KEY,
    customer_id INTEGER,
    customer_name TEXT,
    customer_phone TEXT,
    customer_email TEXT,
    customer_address TEXT,
    payment_method_id INTEGER,
    overall_discount REAL,
    is_discount_fixed INTEGER,
    amount_tendered REAL,
    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invoice_sequences (
    payment_type_code TEXT PRIMARY KEY,
    last_sequence INTEGER NOT NULL
);

CREATE TABLE pos_open_bill_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    bill_index INTEGER,
    product_id INTEGER,
    quantity INTEGER,
    price_per_unit REAL,
    discount_value REAL,
    discount_type TEXT,
    FOREIGN KEY(bill_index) REFERENCES pos_open_bills(bill_index) ON DELETE CASCADE,
    FOREIGN KEY(product_id) REFERENCES products(id)
);

-- Indexes
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_sessions_token ON sessions(token);
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_customers_name ON customers(name);
CREATE INDEX idx_customers_phone ON customers(phone);
CREATE INDEX idx_customers_deleted ON customers(deleted_at);
CREATE INDEX idx_categories_parent ON categories(parent_id);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_deleted ON products(deleted_at);
CREATE INDEX idx_inventory_lots_product ON inventory_lots(product_id);
CREATE INDEX idx_inventory_adjustments_product ON inventory_adjustments(product_id);
CREATE INDEX idx_product_stock_cache_stock ON product_stock_cache(current_stock);
CREATE INDEX idx_sales_invoice ON sales(invoice_number);
CREATE INDEX idx_sales_date ON sales(sale_date);
CREATE INDEX idx_sales_customer ON sales(customer_id);
CREATE INDEX idx_sale_items_sale ON sale_items(sale_id);
CREATE INDEX idx_sale_items_product ON sale_items(product_id);
CREATE INDEX idx_legacy_sales_invoice ON legacy_sales(invoice_number);
CREATE INDEX idx_legacy_sales_date ON legacy_sales(sale_date);
CREATE INDEX idx_transactions_sale ON transactions(sale_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_product_flow_product ON product_flow(product_id);
CREATE INDEX idx_product_flow_date ON product_flow(event_date);
CREATE INDEX idx_audit_log_user ON audit_log(user_id);
CREATE INDEX idx_audit_log_date ON audit_log(created_at);
CREATE INDEX idx_drafts_type_user ON drafts(type, user_id);

-- Initial Seed Data
INSERT INTO payment_methods (id, name, code, is_active) VALUES 
(1, 'Cash', 'CASH', 1),
(2, 'Card', 'CARD', 1),
(3, 'UPI', 'UPI', 1),
(4, 'Store Credit', 'CREDIT', 1);

-- Triggers
CREATE TRIGGER customers_updated_at_trig AFTER UPDATE ON customers BEGIN
  UPDATE customers SET updated_at = CURRENT_TIMESTAMP WHERE id = OLD.id;
END;

CREATE TRIGGER trg_update_stock_cache_lot_insert AFTER INSERT ON inventory_lots BEGIN
  INSERT INTO product_stock_cache (product_id, current_stock, last_updated)
  VALUES (NEW.product_id, NEW.quantity, CURRENT_TIMESTAMP)
  ON CONFLICT(product_id) DO UPDATE SET
    current_stock = current_stock + NEW.quantity,
    last_updated = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER trg_update_stock_cache_adjustment AFTER INSERT ON inventory_adjustments BEGIN
  INSERT INTO product_stock_cache (product_id, current_stock, last_updated)
  VALUES (NEW.product_id, NEW.quantity_change, CURRENT_TIMESTAMP)
  ON CONFLICT(product_id) DO UPDATE SET
    current_stock = current_stock + NEW.quantity_change,
    last_updated = CURRENT_TIMESTAMP;
END;
