-- Source: database/schema_meta.sql
CREATE TABLE IF NOT EXISTS schema_migrations (
  version TEXT PRIMARY KEY,
  applied_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_metadata (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

-- Source: database/users_and_security.sql
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  is_active INTEGER DEFAULT 1 CHECK(is_active IN (0,1)),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME
);

CREATE TABLE IF NOT EXISTS roles (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE,
  description TEXT
);

CREATE TABLE IF NOT EXISTS permissions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  key TEXT NOT NULL UNIQUE,
  description TEXT
);

CREATE TABLE IF NOT EXISTS role_permissions (
  role_id INTEGER NOT NULL,
  permission_id INTEGER NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
  FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_roles (
  user_id INTEGER NOT NULL,
  role_id INTEGER NOT NULL,
  PRIMARY KEY (user_id, role_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_users_name ON users(name);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions(permission_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);

CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY,
  user_id INTEGER NOT NULL,
  token TEXT NOT NULL UNIQUE,
  expires_at INTEGER NOT NULL,
  data TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions(token);
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);

CREATE TABLE IF NOT EXISTS user_permissions (
  user_id INTEGER NOT NULL,
  permission_id INTEGER NOT NULL,
  granted INTEGER NOT NULL DEFAULT 1 CHECK(granted IN (0,1)),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, permission_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_permissions_user_id ON user_permissions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_permissions_permission_id ON user_permissions(permission_id);

-- Source: database/customers.sql
CREATE TABLE IF NOT EXISTS customers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL CHECK (length(trim(name)) > 0),
  phone TEXT,
  email TEXT,
  address TEXT,
  created_at TEXT DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
  deleted_at TEXT
) STRICT;

CREATE INDEX IF NOT EXISTS idx_customers_name ON customers(name);
CREATE INDEX IF NOT EXISTS idx_customers_phone ON customers(phone);
CREATE INDEX IF NOT EXISTS idx_customers_email ON customers(email);
CREATE INDEX IF NOT EXISTS idx_customers_created_at ON customers(created_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_phone_unique_active
  ON customers(phone)
  WHERE phone IS NOT NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_email_unique_active
  ON customers(email)
  WHERE email IS NOT NULL AND deleted_at IS NULL;

CREATE TRIGGER IF NOT EXISTS customers_updated_at_trig
AFTER UPDATE ON customers
BEGIN
  UPDATE customers SET updated_at = CURRENT_TIMESTAMP WHERE id = OLD.id;
END;

-- Source: database/products.sql
CREATE TABLE IF NOT EXISTS categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE,
  parent_id INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  FOREIGN KEY (parent_id) REFERENCES categories(id)
);

CREATE TABLE IF NOT EXISTS products (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  description TEXT,
  category_id INTEGER,
  tax_category_id INTEGER,
  status TEXT CHECK(status IN ('active','inactive','discontinued')) DEFAULT 'active',
  image_path TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  FOREIGN KEY (category_id) REFERENCES categories(id),
  FOREIGN KEY (tax_category_id) REFERENCES tax_categories(id)
);

CREATE TABLE IF NOT EXISTS variants (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  sku TEXT,
  mrp NUMERIC(10,2) NOT NULL CHECK(mrp >= 0),
  cost_price NUMERIC(10,2) NOT NULL CHECK(cost_price >= 0),
  status TEXT CHECK(status IN ('active','inactive','discontinued')) DEFAULT 'active',
  is_default INTEGER DEFAULT 0 CHECK(is_default IN (0,1)),
  stock_alert_cap INTEGER DEFAULT 10 CHECK(stock_alert_cap >= 0),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_one_default_variant
ON variants(product_id)
WHERE is_default = 1 AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON categories(parent_id);
CREATE INDEX IF NOT EXISTS idx_categories_deleted_at ON categories(deleted_at);

CREATE INDEX IF NOT EXISTS idx_products_category_id ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);
CREATE INDEX IF NOT EXISTS idx_products_status ON products(status);
CREATE INDEX IF NOT EXISTS idx_products_deleted_at ON products(deleted_at);
CREATE INDEX IF NOT EXISTS idx_products_tax_category_id ON products(tax_category_id);
CREATE INDEX IF NOT EXISTS idx_products_status_deleted ON products(status, deleted_at);

CREATE INDEX IF NOT EXISTS idx_variants_product_id ON variants(product_id);
CREATE INDEX IF NOT EXISTS idx_variants_name ON variants(name);
CREATE INDEX IF NOT EXISTS idx_variants_sku ON variants(sku) WHERE sku IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_variants_status ON variants(status);
CREATE INDEX IF NOT EXISTS idx_variants_deleted_at ON variants(deleted_at);
CREATE INDEX IF NOT EXISTS idx_variants_product_status ON variants(product_id, status, deleted_at);

-- Source: database/pricing_and_tax.sql
DROP TABLE IF EXISTS taxes;
DROP TABLE IF EXISTS product_taxes;
DROP TABLE IF EXISTS variant_price_history;

CREATE TABLE IF NOT EXISTS tax_categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE,
  description TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_profiles (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  country_code TEXT,
  region_code TEXT,
  pricing_mode TEXT CHECK(pricing_mode IN ('INCLUSIVE', 'EXCLUSIVE')) NOT NULL,
  is_active INTEGER DEFAULT 0 CHECK(is_active IN (0,1)),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER IF NOT EXISTS unique_active_profile
BEFORE UPDATE OF is_active ON tax_profiles
WHEN NEW.is_active = 1
BEGIN
  UPDATE tax_profiles SET is_active = 0 WHERE id != NEW.id AND is_active = 1;
END;

CREATE TRIGGER IF NOT EXISTS unique_active_profile_insert
BEFORE INSERT ON tax_profiles
WHEN NEW.is_active = 1
BEGIN
  UPDATE tax_profiles SET is_active = 0 WHERE is_active = 1;
END;


CREATE TABLE IF NOT EXISTS tax_rules (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tax_profile_id INTEGER NOT NULL,
  tax_category_id INTEGER,
  rule_scope TEXT CHECK(rule_scope IN ('ITEM', 'INVOICE')) DEFAULT 'ITEM',
  min_price REAL,
  max_price REAL,
  min_invoice_total REAL,
  max_invoice_total REAL,
  customer_type TEXT,
  rate_percent REAL NOT NULL,
  is_compound INTEGER DEFAULT 0 CHECK(is_compound IN (0,1)),
  priority INTEGER DEFAULT 0,
  valid_from DATE,
  valid_to DATE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tax_profile_id) REFERENCES tax_profiles(id) ON DELETE CASCADE,
  FOREIGN KEY (tax_category_id) REFERENCES tax_categories(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_tax_rules_profile_id ON tax_rules(tax_profile_id);
CREATE INDEX IF NOT EXISTS idx_tax_rules_category_id ON tax_rules(tax_category_id);

-- Source: database/inventory.sql
CREATE TABLE IF NOT EXISTS payment_policies (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE,
  days_to_pay INTEGER NOT NULL DEFAULT 0,
  description TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME
);

INSERT INTO payment_policies (id, name, days_to_pay, description) 
VALUES (1, 'Pay when received', 0, 'Payment is due immediately upon receipt of goods') 
ON CONFLICT(id) DO NOTHING;

CREATE TABLE IF NOT EXISTS suppliers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE,
  contact_person TEXT,
  phone TEXT,
  email TEXT,
  address TEXT,
  gstin TEXT,
  payment_policy_id INTEGER REFERENCES payment_policies(id) DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME
);

CREATE TABLE IF NOT EXISTS purchase_orders (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  supplier_id INTEGER NOT NULL,
  status TEXT CHECK(status IN ('pending','received','cancelled')) NOT NULL DEFAULT 'pending',
  order_date DATETIME DEFAULT CURRENT_TIMESTAMP,
  received_date DATETIME,
  created_by INTEGER NOT NULL,
  FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
  FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS purchase_order_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  purchase_order_id INTEGER NOT NULL,
  variant_id INTEGER NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity > 0),
  unit_cost NUMERIC(10,2) NOT NULL CHECK(unit_cost >= 0),
  UNIQUE(purchase_order_id, variant_id),
  FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
  FOREIGN KEY (variant_id) REFERENCES variants(id)
);

CREATE TABLE IF NOT EXISTS inventory_lots (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  variant_id INTEGER NOT NULL,
  batch_number TEXT,
  manufactured_date DATETIME,
  expiry_date DATETIME,
  quantity INTEGER NOT NULL CHECK(quantity > 0),
  unit_cost NUMERIC(10,2) NOT NULL CHECK(unit_cost >= 0),
  purchase_order_item_id INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (variant_id) REFERENCES variants(id),
  FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items(id)
);

CREATE TABLE IF NOT EXISTS inventory_adjustments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  variant_id INTEGER NOT NULL,
  lot_id INTEGER,
  quantity_change INTEGER NOT NULL,
  reason TEXT CHECK(reason IN ('sale','return','confirm_receive','spoilage','damage','theft','correction')) NOT NULL,
  reference_type TEXT,
  reference_id INTEGER,
  adjusted_by INTEGER NOT NULL,
  adjusted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (variant_id) REFERENCES variants(id),
  FOREIGN KEY (lot_id) REFERENCES inventory_lots(id),
  FOREIGN KEY (adjusted_by) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_purchase_orders_supplier_id ON purchase_orders(supplier_id);
CREATE INDEX IF NOT EXISTS idx_suppliers_deleted_at ON suppliers(deleted_at);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_status ON purchase_orders(status);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_order_date ON purchase_orders(order_date);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_created_by ON purchase_orders(created_by);
CREATE INDEX IF NOT EXISTS idx_purchase_order_items_po_id ON purchase_order_items(purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_purchase_order_items_variant_id ON purchase_order_items(variant_id);
CREATE INDEX IF NOT EXISTS idx_inventory_lots_variant_id ON inventory_lots(variant_id);
CREATE INDEX IF NOT EXISTS idx_inventory_lots_expiry_date ON inventory_lots(expiry_date);
CREATE INDEX IF NOT EXISTS idx_inventory_lots_purchase_order_item_id ON inventory_lots(purchase_order_item_id);
CREATE INDEX IF NOT EXISTS idx_inventory_adjustments_variant_id ON inventory_adjustments(variant_id);
CREATE INDEX IF NOT EXISTS idx_inventory_adjustments_lot_id ON inventory_adjustments(lot_id);
CREATE INDEX IF NOT EXISTS idx_inventory_adjustments_adjusted_at ON inventory_adjustments(adjusted_at);

-- Triggers from 009_purchase_module_integrity.sql
CREATE TRIGGER IF NOT EXISTS trg_purchase_orders_require_creator_insert
BEFORE INSERT ON purchase_orders
FOR EACH ROW
WHEN NEW.created_by IS NULL
BEGIN
  SELECT RAISE(ABORT, 'purchase_orders.created_by is required');
END;

CREATE TRIGGER IF NOT EXISTS trg_purchase_orders_require_creator_update
BEFORE UPDATE ON purchase_orders
FOR EACH ROW
WHEN NEW.created_by IS NULL
BEGIN
  SELECT RAISE(ABORT, 'purchase_orders.created_by is required');
END;

CREATE TRIGGER IF NOT EXISTS trg_purchase_orders_received_date_insert
BEFORE INSERT ON purchase_orders
FOR EACH ROW
WHEN NEW.status = 'received' AND NEW.received_date IS NULL
BEGIN
  SELECT RAISE(ABORT, 'received purchase order must have received_date');
END;

CREATE TRIGGER IF NOT EXISTS trg_purchase_orders_received_date_update
BEFORE UPDATE ON purchase_orders
FOR EACH ROW
WHEN NEW.status = 'received' AND NEW.received_date IS NULL
BEGIN
  SELECT RAISE(ABORT, 'received purchase order must have received_date');
END;

CREATE TRIGGER IF NOT EXISTS trg_purchase_order_items_validate_insert
BEFORE INSERT ON purchase_order_items
FOR EACH ROW
WHEN NEW.quantity <= 0
  OR NEW.unit_cost < 0
  OR EXISTS (
    SELECT 1
    FROM purchase_order_items poi
    WHERE poi.purchase_order_id = NEW.purchase_order_id
      AND poi.variant_id = NEW.variant_id
  )
BEGIN
  SELECT RAISE(ABORT, 'invalid purchase_order_items row');
END;

CREATE TRIGGER IF NOT EXISTS trg_purchase_order_items_validate_update
BEFORE UPDATE ON purchase_order_items
FOR EACH ROW
WHEN NEW.quantity <= 0
  OR NEW.unit_cost < 0
  OR EXISTS (
    SELECT 1
    FROM purchase_order_items poi
    WHERE poi.purchase_order_id = NEW.purchase_order_id
      AND poi.variant_id = NEW.variant_id
      AND poi.id != NEW.id
  )
BEGIN
  SELECT RAISE(ABORT, 'invalid purchase_order_items row');
END;

-- Source: database/sales.sql
CREATE TABLE IF NOT EXISTS sales (
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

CREATE TABLE IF NOT EXISTS sale_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sale_id INTEGER NOT NULL,
  variant_id INTEGER NOT NULL,
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
  FOREIGN KEY (variant_id) REFERENCES variants(id)
);

CREATE INDEX IF NOT EXISTS idx_sales_customer_id ON sales(customer_id);
CREATE INDEX IF NOT EXISTS idx_sales_user_id ON sales(user_id);
CREATE INDEX IF NOT EXISTS idx_sales_date ON sales(sale_date);
CREATE INDEX IF NOT EXISTS idx_sales_status ON sales(status);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_number ON sales(invoice_number);
CREATE INDEX IF NOT EXISTS idx_sales_fulfillment_status ON sales(fulfillment_status);
CREATE INDEX IF NOT EXISTS idx_sales_status_date ON sales(status, sale_date DESC);

CREATE INDEX IF NOT EXISTS idx_sale_items_sale_id ON sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_variant_id ON sale_items(variant_id);

-- Source: database/payments.sql
CREATE TABLE IF NOT EXISTS payment_methods (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE,
  code TEXT,
  is_active INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0,1))
);

CREATE TABLE IF NOT EXISTS transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sale_id INTEGER,
  purchase_order_id INTEGER,
  amount NUMERIC(10,2) NOT NULL,
  type TEXT CHECK(type IN ('payment','refund','purchase','purchase_refund')) NOT NULL,
  payment_method_id INTEGER NOT NULL,
  status TEXT CHECK(status IN ('completed','pending','cancelled')) NOT NULL DEFAULT 'completed',
  transaction_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK(amount != 0),
  CHECK(
    (type IN ('payment', 'purchase_refund') AND amount > 0) OR
    (type IN ('refund', 'purchase') AND amount < 0)
  ),
  CHECK(
    (sale_id IS NOT NULL AND purchase_order_id IS NULL) OR
    (sale_id IS NULL AND purchase_order_id IS NOT NULL)
  ),
  FOREIGN KEY (sale_id) REFERENCES sales(id),
  FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
  FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id)
);

CREATE INDEX IF NOT EXISTS idx_transactions_sale_id ON transactions(sale_id);
CREATE INDEX IF NOT EXISTS idx_transactions_purchase_order_id ON transactions(purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_transactions_payment_method_id ON transactions(payment_method_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(type);

-- Source: database/returns_and_refunds.sql
CREATE TABLE IF NOT EXISTS returns (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sale_id INTEGER NOT NULL,
  user_id INTEGER NOT NULL,
  reason TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (sale_id) REFERENCES sales(id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS return_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  return_id INTEGER NOT NULL,
  sale_item_id INTEGER NOT NULL,
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  refund_amount NUMERIC(10,2) NOT NULL CHECK (refund_amount >= 0),
  FOREIGN KEY (return_id) REFERENCES returns(id) ON DELETE CASCADE,
  FOREIGN KEY (sale_item_id) REFERENCES sale_items(id)
);

CREATE INDEX IF NOT EXISTS idx_returns_sale_id ON returns(sale_id);
CREATE INDEX IF NOT EXISTS idx_returns_user_id ON returns(user_id);
CREATE INDEX IF NOT EXISTS idx_returns_created_at ON returns(created_at);
CREATE INDEX IF NOT EXISTS idx_returns_sale_created ON returns(sale_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_return_items_return_id ON return_items(return_id);
CREATE INDEX IF NOT EXISTS idx_return_items_sale_item_id ON return_items(sale_item_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_return_items_return_sale_item ON return_items(return_id, sale_item_id);

-- Source: database/reporting.sql
-- Product Flow Analysis (materialized log)
CREATE TABLE IF NOT EXISTS product_flow (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  variant_id INTEGER NOT NULL,
  event_type TEXT CHECK(event_type IN ('purchase','sale','return','adjustment')) NOT NULL,
  quantity INTEGER NOT NULL,
  reference_type TEXT,
  reference_id INTEGER,
  event_date DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (variant_id) REFERENCES variants(id)
);

-- Aggregated Sales Reports (optional caching)
CREATE TABLE IF NOT EXISTS sales_reports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  report_type TEXT CHECK(report_type IN ('daily','monthly','yearly')) NOT NULL,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  total_sales NUMERIC(10,2) NOT NULL,
  total_tax NUMERIC(10,2) NOT NULL,
  total_discount NUMERIC(10,2) NOT NULL,
  total_transactions INTEGER NOT NULL,
  generated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_flow_variant_id ON product_flow(variant_id);
CREATE INDEX IF NOT EXISTS idx_product_flow_event_date ON product_flow(event_date);
CREATE INDEX IF NOT EXISTS idx_product_flow_event_type ON product_flow(event_type);
CREATE INDEX IF NOT EXISTS idx_product_flow_ref ON product_flow(reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_sales_reports_period_start ON sales_reports(period_start);
CREATE INDEX IF NOT EXISTS idx_sales_reports_report_type ON sales_reports(report_type);

-- Source: database/audit.sql
CREATE TABLE IF NOT EXISTS audit_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  action TEXT NOT NULL,
  table_name TEXT,
  row_id INTEGER,
  old_data TEXT,
  new_data TEXT,
  event_details TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_table_row ON audit_log(table_name, row_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at);

