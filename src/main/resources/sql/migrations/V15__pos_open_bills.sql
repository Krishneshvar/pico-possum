CREATE TABLE IF NOT EXISTS pos_open_bills (
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

CREATE TABLE IF NOT EXISTS pos_open_bill_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    bill_index INTEGER,
    variant_id INTEGER,
    quantity INTEGER,
    price_per_unit REAL,
    discount_value REAL,
    discount_type TEXT,
    FOREIGN KEY(bill_index) REFERENCES pos_open_bills(bill_index) ON DELETE CASCADE
);

CREATE INDEX idx_pos_open_bill_items_index ON pos_open_bill_items(bill_index);
