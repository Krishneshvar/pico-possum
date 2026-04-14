-- V17__add_product_deleted_inventory_reason.sql

-- 1. Rename existing table
ALTER TABLE inventory_adjustments RENAME TO old_inventory_adjustments;

-- 2. Create new table with updated CHECK constraint
CREATE TABLE inventory_adjustments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id INTEGER NOT NULL,
  lot_id INTEGER,
  quantity_change INTEGER NOT NULL,
  reason TEXT CHECK(reason IN ('sale','return','confirm_receive','spoilage','damage','theft','correction','product_deleted')) NOT NULL,
  reference_type TEXT,
  reference_id INTEGER,
  adjusted_by INTEGER NOT NULL,
  adjusted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (product_id) REFERENCES products(id),
  FOREIGN KEY (lot_id) REFERENCES inventory_lots(id),
  FOREIGN KEY (adjusted_by) REFERENCES users(id)
);

-- 3. Copy data
INSERT INTO inventory_adjustments (id, product_id, lot_id, quantity_change, reason, reference_type, reference_id, adjusted_by, adjusted_at)
SELECT id, product_id, lot_id, quantity_change, reason, reference_type, reference_id, adjusted_by, adjusted_at
FROM old_inventory_adjustments;

-- 4. Drop old table
DROP TABLE old_inventory_adjustments;

-- 5. Re-create indexes
CREATE INDEX idx_inventory_adjustments_product_date ON inventory_adjustments(product_id, adjusted_at DESC);

-- 6. Re-create triggers (important!)
DROP TRIGGER IF EXISTS trg_update_stock_cache_adjustment;
CREATE TRIGGER trg_update_stock_cache_adjustment
AFTER INSERT ON inventory_adjustments
BEGIN
  INSERT INTO product_stock_cache (product_id, current_stock, last_updated)
  VALUES (NEW.product_id, NEW.quantity_change, CURRENT_TIMESTAMP)
  ON CONFLICT(product_id) DO UPDATE SET
    current_stock = current_stock + NEW.quantity_change,
    last_updated = CURRENT_TIMESTAMP;
END;
