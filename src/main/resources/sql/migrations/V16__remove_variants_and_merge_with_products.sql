-- V16__remove_variants_and_merge_with_products.sql

-- 1. Add variant columns to products
ALTER TABLE products ADD COLUMN sku TEXT;
ALTER TABLE products ADD COLUMN mrp NUMERIC(10,2) DEFAULT 0;
ALTER TABLE products ADD COLUMN cost_price NUMERIC(10,2) DEFAULT 0;
ALTER TABLE products ADD COLUMN stock_alert_cap INTEGER DEFAULT 10;

-- 2. Migrate data from variants to products
-- We take the default variant if it exists, otherwise the first variant created
UPDATE products
SET
    sku = (SELECT sku FROM variants WHERE product_id = products.id AND (is_default = 1 OR NOT EXISTS (SELECT 1 FROM variants v2 WHERE v2.product_id = products.id AND v2.is_default = 1)) LIMIT 1),
    mrp = (SELECT mrp FROM variants WHERE product_id = products.id AND (is_default = 1 OR NOT EXISTS (SELECT 1 FROM variants v2 WHERE v2.product_id = products.id AND v2.is_default = 1)) LIMIT 1),
    cost_price = (SELECT cost_price FROM variants WHERE product_id = products.id AND (is_default = 1 OR NOT EXISTS (SELECT 1 FROM variants v2 WHERE v2.product_id = products.id AND v2.is_default = 1)) LIMIT 1),
    stock_alert_cap = (SELECT stock_alert_cap FROM variants WHERE product_id = products.id AND (is_default = 1 OR NOT EXISTS (SELECT 1 FROM variants v2 WHERE v2.product_id = products.id AND v2.is_default = 1)) LIMIT 1);

-- 3. Update existing tables to use product_id

-- Drop dependent triggers first
DROP TRIGGER IF EXISTS trg_purchase_order_items_validate_insert;
DROP TRIGGER IF EXISTS trg_purchase_order_items_validate_update;
DROP TRIGGER IF EXISTS trg_update_stock_cache_lot_insert;
DROP TRIGGER IF EXISTS trg_update_stock_cache_adjustment;

-- Rename old tables
ALTER TABLE purchase_order_items RENAME TO old_purchase_order_items;
ALTER TABLE inventory_lots RENAME TO old_inventory_lots;
ALTER TABLE inventory_adjustments RENAME TO old_inventory_adjustments;
ALTER TABLE sale_items RENAME TO old_sale_items;
ALTER TABLE product_flow RENAME TO old_product_flow;
ALTER TABLE product_stock_cache RENAME TO old_product_stock_cache;

-- Create new tables
CREATE TABLE purchase_order_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  purchase_order_id INTEGER NOT NULL,
  product_id INTEGER NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity > 0),
  unit_cost NUMERIC(10,2) NOT NULL CHECK(unit_cost >= 0),
  UNIQUE(purchase_order_id, product_id),
  FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
  FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE inventory_lots (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id INTEGER NOT NULL,
  batch_number TEXT,
  manufactured_date DATETIME,
  expiry_date DATETIME,
  quantity INTEGER NOT NULL CHECK(quantity > 0),
  unit_cost NUMERIC(10,2) NOT NULL CHECK(unit_cost >= 0),
  purchase_order_item_id INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (product_id) REFERENCES products(id),
  FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items(id)
);

CREATE TABLE inventory_adjustments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id INTEGER NOT NULL,
  lot_id INTEGER,
  quantity_change INTEGER NOT NULL,
  reason TEXT CHECK(reason IN ('sale','return','confirm_receive','spoilage','damage','theft','correction')) NOT NULL,
  reference_type TEXT,
  reference_id INTEGER,
  adjusted_by INTEGER NOT NULL,
  adjusted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (product_id) REFERENCES products(id),
  FOREIGN KEY (lot_id) REFERENCES inventory_lots(id),
  FOREIGN KEY (adjusted_by) REFERENCES users(id)
);

CREATE TABLE sale_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sale_id INTEGER NOT NULL,
  product_id INTEGER NOT NULL,
  quantity INTEGER NOT NULL,
  price_per_unit NUMERIC(10,2) NOT NULL,
  cost_per_unit NUMERIC(10,2) NOT NULL,
  tax_rate REAL,
  tax_amount NUMERIC(10,2),
  applied_tax_rate REAL,
  applied_tax_amount NUMERIC(10,2),
  tax_rule_snapshot TEXT,
  discount_amount NUMERIC(10,2) DEFAULT 0,
  FOREIGN KEY (sale_id) REFERENCES sales(id),
  FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE product_flow (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id INTEGER NOT NULL,
  event_type TEXT CHECK(event_type IN ('purchase','sale','return','adjustment')) NOT NULL,
  quantity INTEGER NOT NULL,
  reference_type TEXT,
  reference_id INTEGER,
  event_date DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE product_stock_cache (
  product_id INTEGER PRIMARY KEY,
  current_stock INTEGER NOT NULL DEFAULT 0,
  last_updated DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- Migrate data
INSERT INTO purchase_order_items (id, purchase_order_id, product_id, quantity, unit_cost)
SELECT poi.id, poi.purchase_order_id, v.product_id, poi.quantity, poi.unit_cost
FROM old_purchase_order_items poi
JOIN variants v ON poi.variant_id = v.id;

INSERT INTO inventory_lots (id, product_id, batch_number, manufactured_date, expiry_date, quantity, unit_cost, purchase_order_item_id, created_at)
SELECT il.id, v.product_id, il.batch_number, il.manufactured_date, il.expiry_date, il.quantity, il.unit_cost, il.purchase_order_item_id, il.created_at
FROM old_inventory_lots il
JOIN variants v ON il.variant_id = v.id;

INSERT INTO inventory_adjustments (id, product_id, lot_id, quantity_change, reason, reference_type, reference_id, adjusted_by, adjusted_at)
SELECT ia.id, v.product_id, ia.lot_id, ia.quantity_change, ia.reason, ia.reference_type, ia.reference_id, ia.adjusted_by, ia.adjusted_at
FROM old_inventory_adjustments ia
JOIN variants v ON ia.variant_id = v.id;

INSERT INTO sale_items (id, sale_id, product_id, quantity, price_per_unit, cost_per_unit, tax_rate, tax_amount, applied_tax_rate, applied_tax_amount, tax_rule_snapshot, discount_amount)
SELECT si.id, si.sale_id, v.product_id, si.quantity, si.price_per_unit, si.cost_per_unit, si.tax_rate, si.tax_amount, si.applied_tax_rate, si.applied_tax_amount, si.tax_rule_snapshot, si.discount_amount
FROM old_sale_items si
JOIN variants v ON si.variant_id = v.id;

INSERT INTO product_flow (id, product_id, event_type, quantity, reference_type, reference_id, event_date)
SELECT pf.id, v.product_id, pf.event_type, pf.quantity, pf.reference_type, pf.reference_id, pf.event_date
FROM old_product_flow pf
JOIN variants v ON pf.variant_id = v.id;

INSERT INTO product_stock_cache (product_id, current_stock, last_updated)
SELECT v.product_id, SUM(psc.current_stock), MAX(psc.last_updated)
FROM old_product_stock_cache psc
JOIN variants v ON psc.variant_id = v.id
GROUP BY v.product_id;

-- Drop old tables and variants table
DROP TABLE old_purchase_order_items;
DROP TABLE old_inventory_lots;
DROP TABLE old_inventory_adjustments;
DROP TABLE old_sale_items;
DROP TABLE old_product_flow;
DROP TABLE old_product_stock_cache;
DROP TABLE variants;

-- Re-create indexes
CREATE INDEX idx_products_sku ON products(sku) WHERE sku IS NOT NULL;
CREATE INDEX idx_purchase_order_items_product_id ON purchase_order_items(product_id);
CREATE INDEX idx_inventory_lots_product_id ON inventory_lots(product_id);
CREATE INDEX idx_inventory_lots_product_expiry ON inventory_lots(product_id, expiry_date);
CREATE INDEX idx_inventory_adjustments_product_date ON inventory_adjustments(product_id, adjusted_at DESC);
CREATE INDEX idx_sale_items_product_sale ON sale_items(product_id, sale_id);
CREATE INDEX idx_product_flow_product_date ON product_flow(product_id, event_date DESC);
CREATE INDEX idx_product_stock_cache_stock ON product_stock_cache(current_stock);

-- Re-create triggers
CREATE TRIGGER trg_purchase_order_items_validate_insert
BEFORE INSERT ON purchase_order_items
FOR EACH ROW
WHEN NEW.quantity <= 0
  OR NEW.unit_cost < 0
  OR EXISTS (
    SELECT 1
    FROM purchase_order_items poi
    WHERE poi.purchase_order_id = NEW.purchase_order_id
      AND poi.product_id = NEW.product_id
  )
BEGIN
  SELECT RAISE(ABORT, 'invalid purchase_order_items row');
END;

CREATE TRIGGER trg_purchase_order_items_validate_update
BEFORE UPDATE ON purchase_order_items
FOR EACH ROW
WHEN NEW.quantity <= 0
  OR NEW.unit_cost < 0
  OR EXISTS (
    SELECT 1
    FROM purchase_order_items poi
    WHERE poi.purchase_order_id = NEW.purchase_order_id
      AND poi.product_id = NEW.product_id
      AND poi.id != NEW.id
  )
BEGIN
  SELECT RAISE(ABORT, 'invalid purchase_order_items row');
END;

CREATE TRIGGER trg_update_stock_cache_lot_insert
AFTER INSERT ON inventory_lots
BEGIN
  INSERT INTO product_stock_cache (product_id, current_stock, last_updated)
  VALUES (NEW.product_id, NEW.quantity, CURRENT_TIMESTAMP)
  ON CONFLICT(product_id) DO UPDATE SET
    current_stock = current_stock + NEW.quantity,
    last_updated = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER trg_update_stock_cache_adjustment
AFTER INSERT ON inventory_adjustments
BEGIN
  INSERT INTO product_stock_cache (product_id, current_stock, last_updated)
  VALUES (NEW.product_id, NEW.quantity_change, CURRENT_TIMESTAMP)
  ON CONFLICT(product_id) DO UPDATE SET
    current_stock = current_stock + NEW.quantity_change,
    last_updated = CURRENT_TIMESTAMP;
END;
