-- Phase 2: Performance Optimization & Authorization Enhancement

-- Add audit log integrity hash column
ALTER TABLE audit_log ADD COLUMN integrity_hash TEXT;
ALTER TABLE audit_log ADD COLUMN previous_hash TEXT;

CREATE INDEX IF NOT EXISTS idx_audit_log_integrity ON audit_log(integrity_hash);

-- Role hierarchy table
CREATE TABLE IF NOT EXISTS role_hierarchy (
  parent_role_id INTEGER NOT NULL,
  child_role_id INTEGER NOT NULL,
  priority INTEGER NOT NULL DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (parent_role_id, child_role_id),
  FOREIGN KEY (parent_role_id) REFERENCES roles(id) ON DELETE CASCADE,
  FOREIGN KEY (child_role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_role_hierarchy_parent ON role_hierarchy(parent_role_id);
CREATE INDEX IF NOT EXISTS idx_role_hierarchy_child ON role_hierarchy(child_role_id);

-- Permission delegation table
CREATE TABLE IF NOT EXISTS permission_delegations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  delegator_user_id INTEGER NOT NULL,
  delegatee_user_id INTEGER NOT NULL,
  permission_id INTEGER NOT NULL,
  granted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  expires_at DATETIME,
  revoked_at DATETIME,
  FOREIGN KEY (delegator_user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (delegatee_user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_permission_delegations_delegatee ON permission_delegations(delegatee_user_id);
CREATE INDEX IF NOT EXISTS idx_permission_delegations_expires ON permission_delegations(expires_at);

-- Time-based permissions table
CREATE TABLE IF NOT EXISTS time_based_permissions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  permission_id INTEGER NOT NULL,
  valid_from DATETIME NOT NULL,
  valid_to DATETIME NOT NULL,
  days_of_week TEXT, -- JSON array: [1,2,3,4,5] for Mon-Fri
  time_from TIME,
  time_to TIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_time_based_permissions_user ON time_based_permissions(user_id);
CREATE INDEX IF NOT EXISTS idx_time_based_permissions_valid ON time_based_permissions(valid_from, valid_to);

-- Performance: Composite indexes for common query patterns

-- Sales queries by date range and status
CREATE INDEX IF NOT EXISTS idx_sales_date_status ON sales(sale_date DESC, status);
CREATE INDEX IF NOT EXISTS idx_sales_customer_date ON sales(customer_id, sale_date DESC);
CREATE INDEX IF NOT EXISTS idx_sales_user_date ON sales(user_id, sale_date DESC);

-- Sale items with variant lookup
CREATE INDEX IF NOT EXISTS idx_sale_items_variant_sale ON sale_items(variant_id, sale_id);

-- Transactions by date and type
CREATE INDEX IF NOT EXISTS idx_transactions_date_type ON transactions(transaction_date DESC, type);
CREATE INDEX IF NOT EXISTS idx_transactions_method_date ON transactions(payment_method_id, transaction_date DESC);

-- Inventory queries
CREATE INDEX IF NOT EXISTS idx_inventory_lots_variant_expiry ON inventory_lots(variant_id, expiry_date);
CREATE INDEX IF NOT EXISTS idx_inventory_adjustments_variant_date ON inventory_adjustments(variant_id, adjusted_at DESC);

-- Product flow for reporting
CREATE INDEX IF NOT EXISTS idx_product_flow_variant_date ON product_flow(variant_id, event_date DESC);
CREATE INDEX IF NOT EXISTS idx_product_flow_date_type ON product_flow(event_date DESC, event_type);

-- Variants with product lookup
CREATE INDEX IF NOT EXISTS idx_variants_product_status ON variants(product_id, status, deleted_at);
CREATE INDEX IF NOT EXISTS idx_variants_sku_status ON variants(sku, status) WHERE sku IS NOT NULL;

-- Purchase orders
CREATE INDEX IF NOT EXISTS idx_purchase_orders_supplier_date ON purchase_orders(supplier_id, order_date DESC);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_status_date ON purchase_orders(status, order_date DESC);

-- Returns
CREATE INDEX IF NOT EXISTS idx_returns_sale_date ON returns(sale_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_return_items_sale_item ON return_items(sale_item_id);

-- User and role lookups
CREATE INDEX IF NOT EXISTS idx_user_roles_user_role ON user_roles(user_id, role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_role_perm ON role_permissions(role_id, permission_id);

-- Audit log performance
CREATE INDEX IF NOT EXISTS idx_audit_log_user_action_date ON audit_log(user_id, action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_table_row ON audit_log(table_name, row_id, created_at DESC);

-- Session performance
CREATE INDEX IF NOT EXISTS idx_sessions_user_expires ON sessions(user_id, expires_at);

-- Materialized view for product stock (updated via triggers)
CREATE TABLE IF NOT EXISTS product_stock_cache (
  variant_id INTEGER PRIMARY KEY,
  current_stock INTEGER NOT NULL DEFAULT 0,
  last_updated DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (variant_id) REFERENCES variants(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_product_stock_cache_stock ON product_stock_cache(current_stock);

-- Trigger to update stock cache on inventory lot insert
CREATE TRIGGER IF NOT EXISTS trg_update_stock_cache_lot_insert
AFTER INSERT ON inventory_lots
BEGIN
  INSERT INTO product_stock_cache (variant_id, current_stock, last_updated)
  VALUES (NEW.variant_id, NEW.quantity, CURRENT_TIMESTAMP)
  ON CONFLICT(variant_id) DO UPDATE SET
    current_stock = current_stock + NEW.quantity,
    last_updated = CURRENT_TIMESTAMP;
END;

-- Trigger to update stock cache on inventory adjustment
CREATE TRIGGER IF NOT EXISTS trg_update_stock_cache_adjustment
AFTER INSERT ON inventory_adjustments
BEGIN
  INSERT INTO product_stock_cache (variant_id, current_stock, last_updated)
  VALUES (NEW.variant_id, NEW.quantity_change, CURRENT_TIMESTAMP)
  ON CONFLICT(variant_id) DO UPDATE SET
    current_stock = current_stock + NEW.quantity_change,
    last_updated = CURRENT_TIMESTAMP;
END;

-- Initialize stock cache for existing variants
INSERT OR IGNORE INTO product_stock_cache (variant_id, current_stock, last_updated)
SELECT 
  v.id,
  COALESCE((SELECT SUM(quantity) FROM inventory_lots WHERE variant_id = v.id), 0)
  + COALESCE((SELECT SUM(quantity_change) FROM inventory_adjustments WHERE variant_id = v.id), 0),
  CURRENT_TIMESTAMP
FROM variants v
WHERE v.deleted_at IS NULL;

-- Query performance monitoring table
CREATE TABLE IF NOT EXISTS query_performance_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  query_name TEXT NOT NULL,
  execution_time_ms INTEGER NOT NULL,
  row_count INTEGER,
  executed_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_query_performance_name_time ON query_performance_log(query_name, execution_time_ms DESC);
CREATE INDEX IF NOT EXISTS idx_query_performance_executed ON query_performance_log(executed_at DESC);

-- Configurable authorization settings
INSERT OR IGNORE INTO security_settings (key, value, description) VALUES
  ('superuser_role', 'admin', 'Role with unrestricted access'),
  ('enable_role_hierarchy', '1', 'Enable role inheritance'),
  ('enable_permission_delegation', '1', 'Allow users to delegate permissions'),
  ('enable_time_based_permissions', '1', 'Enable time-restricted permissions'),
  ('audit_log_integrity_check', '1', 'Enable audit log hash chain verification');

-- Add priority column to roles
ALTER TABLE roles ADD COLUMN priority INTEGER DEFAULT 0;

-- Update default role priorities
UPDATE roles SET priority = 1000 WHERE name = 'admin';
UPDATE roles SET priority = 100 WHERE name = 'manager';
UPDATE roles SET priority = 50 WHERE name = 'cashier';
UPDATE roles SET priority = 10 WHERE name = 'viewer';

-- Analyze tables for query optimizer
ANALYZE;
