-- V20: Remove purchase module (suppliers, purchase_orders, purchase_order_items)
-- Also drops purchase_order_item_id column from inventory_lots
-- and purchase_order_id column from transactions

-- 1. Drop triggers on purchase_orders
DROP TRIGGER IF EXISTS trg_purchase_orders_require_creator_insert;
DROP TRIGGER IF EXISTS trg_purchase_orders_require_creator_update;
DROP TRIGGER IF EXISTS trg_purchase_orders_received_date_insert;
DROP TRIGGER IF EXISTS trg_purchase_orders_received_date_update;
DROP TRIGGER IF EXISTS trg_purchase_order_items_validate_insert;
DROP TRIGGER IF EXISTS trg_purchase_order_items_validate_update;

-- 2. Drop indexes on purchase tables
DROP INDEX IF EXISTS idx_purchase_orders_supplier_id;
DROP INDEX IF EXISTS idx_suppliers_deleted_at;
DROP INDEX IF EXISTS idx_purchase_orders_status;
DROP INDEX IF EXISTS idx_purchase_orders_order_date;
DROP INDEX IF EXISTS idx_purchase_orders_created_by;
DROP INDEX IF EXISTS idx_purchase_order_items_po_id;
DROP INDEX IF EXISTS idx_purchase_order_items_variant_id;
DROP INDEX IF EXISTS idx_inventory_lots_purchase_order_item_id;

-- 3. Rebuild inventory_lots without purchase_order_item_id
CREATE TABLE inventory_lots_new (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id  INTEGER NOT NULL,
    batch_number TEXT,
    manufactured_date TEXT,
    expiry_date TEXT,
    quantity    INTEGER NOT NULL DEFAULT 0,
    unit_cost   NUMERIC NOT NULL DEFAULT 0,
    created_at  TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id)
);

INSERT INTO inventory_lots_new
    (id, product_id, batch_number, manufactured_date, expiry_date, quantity, unit_cost, created_at)
SELECT  id, product_id, batch_number, manufactured_date, expiry_date, quantity, unit_cost, created_at
FROM inventory_lots;

DROP TABLE inventory_lots;
ALTER TABLE inventory_lots_new RENAME TO inventory_lots;

-- 4. Rebuild transactions without purchase_order_id
CREATE TABLE transactions_new (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id            INTEGER,
    amount             NUMERIC NOT NULL,
    type               TEXT NOT NULL,
    payment_method_id  INTEGER,
    status             TEXT NOT NULL DEFAULT 'completed',
    transaction_date   TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sale_id)           REFERENCES sales(id),
    FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id)
);

INSERT INTO transactions_new
    (id, sale_id, amount, type, payment_method_id, status, transaction_date)
SELECT  id, sale_id, amount, type, payment_method_id, status, transaction_date
FROM transactions
WHERE sale_id IS NOT NULL OR type IN ('payment','refund');

DROP TABLE transactions;
ALTER TABLE transactions_new RENAME TO transactions;

-- 5. Drop purchase tables (order matters due to FKs)
DROP TABLE IF EXISTS purchase_order_items;
DROP TABLE IF EXISTS purchase_orders;
DROP TABLE IF EXISTS suppliers;
