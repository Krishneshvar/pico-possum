-- Phase 3: Tax Engine Enhancement Migration

-- Tax exemption tracking table for audit trail
CREATE TABLE IF NOT EXISTS tax_exemptions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  customer_id INTEGER NOT NULL,
  exemption_type TEXT CHECK(exemption_type IN ('government', 'ngo', 'diplomatic', 'export', 'other')) NOT NULL,
  certificate_number TEXT,
  reason TEXT NOT NULL,
  valid_from DATETIME DEFAULT CURRENT_TIMESTAMP,
  valid_to DATETIME,
  approved_by INTEGER NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
  FOREIGN KEY (approved_by) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_tax_exemptions_customer_id ON tax_exemptions(customer_id);
CREATE INDEX IF NOT EXISTS idx_tax_exemptions_valid_from ON tax_exemptions(valid_from);
CREATE INDEX IF NOT EXISTS idx_tax_exemptions_valid_to ON tax_exemptions(valid_to);
CREATE INDEX IF NOT EXISTS idx_tax_exemptions_approved_by ON tax_exemptions(approved_by);

-- Add rounding strategy to tax profiles
ALTER TABLE tax_profiles ADD COLUMN rounding_strategy TEXT CHECK(rounding_strategy IN ('ITEM_LEVEL', 'INVOICE_LEVEL')) DEFAULT 'INVOICE_LEVEL';
ALTER TABLE tax_profiles ADD COLUMN rounding_mode TEXT CHECK(rounding_mode IN ('HALF_UP', 'HALF_DOWN', 'UP', 'DOWN', 'CEILING', 'FLOOR')) DEFAULT 'HALF_UP';

-- Tax calculation audit log for compliance
CREATE TABLE IF NOT EXISTS tax_calculation_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sale_id INTEGER,
  customer_id INTEGER,
  tax_profile_id INTEGER NOT NULL,
  subtotal NUMERIC(10,2) NOT NULL,
  total_tax NUMERIC(10,2) NOT NULL,
  grand_total NUMERIC(10,2) NOT NULL,
  rounding_strategy TEXT NOT NULL,
  is_tax_exempt INTEGER DEFAULT 0 CHECK(is_tax_exempt IN (0,1)),
  exemption_reason TEXT,
  rules_applied TEXT,
  calculated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (sale_id) REFERENCES sales(id),
  FOREIGN KEY (customer_id) REFERENCES customers(id),
  FOREIGN KEY (tax_profile_id) REFERENCES tax_profiles(id)
);

CREATE INDEX IF NOT EXISTS idx_tax_calculation_log_sale_id ON tax_calculation_log(sale_id);
CREATE INDEX IF NOT EXISTS idx_tax_calculation_log_customer_id ON tax_calculation_log(customer_id);
CREATE INDEX IF NOT EXISTS idx_tax_calculation_log_calculated_at ON tax_calculation_log(calculated_at);

-- Customer type validation constraint
CREATE TABLE IF NOT EXISTS customer_types (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  code TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  description TEXT,
  is_active INTEGER DEFAULT 1 CHECK(is_active IN (0,1)),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Insert default customer types
INSERT INTO customer_types (code, name, description) VALUES
  ('retail', 'Retail Customer', 'Individual retail customers'),
  ('wholesale', 'Wholesale Customer', 'Bulk buyers and distributors'),
  ('corporate', 'Corporate Customer', 'Business entities'),
  ('government', 'Government Entity', 'Government organizations'),
  ('ngo', 'Non-Profit Organization', 'NGOs and charitable organizations');

-- Add foreign key constraint for customer_type (via trigger since SQLite doesn't support ALTER TABLE ADD CONSTRAINT)
CREATE TRIGGER IF NOT EXISTS trg_validate_customer_type_insert
BEFORE INSERT ON customers
FOR EACH ROW
WHEN NEW.customer_type IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM customer_types WHERE code = NEW.customer_type AND is_active = 1)
BEGIN
  SELECT RAISE(ABORT, 'Invalid customer_type: must be a valid active customer type code');
END;

CREATE TRIGGER IF NOT EXISTS trg_validate_customer_type_update
BEFORE UPDATE OF customer_type ON customers
FOR EACH ROW
WHEN NEW.customer_type IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM customer_types WHERE code = NEW.customer_type AND is_active = 1)
BEGIN
  SELECT RAISE(ABORT, 'Invalid customer_type: must be a valid active customer type code');
END;

-- Update existing customers to use valid customer types
UPDATE customers SET customer_type = 'retail' WHERE customer_type IS NULL OR customer_type = '';

-- Tax rule validation enhancements
CREATE TRIGGER IF NOT EXISTS trg_validate_tax_rule_rate_insert
BEFORE INSERT ON tax_rules
FOR EACH ROW
WHEN NEW.rate_percent < 0
BEGIN
  SELECT RAISE(ABORT, 'Tax rate cannot be negative');
END;

CREATE TRIGGER IF NOT EXISTS trg_validate_tax_rule_rate_update
BEFORE UPDATE OF rate_percent ON tax_rules
FOR EACH ROW
WHEN NEW.rate_percent < 0
BEGIN
  SELECT RAISE(ABORT, 'Tax rate cannot be negative');
END;

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_customer_types_code ON customer_types(code);
CREATE INDEX IF NOT EXISTS idx_customer_types_is_active ON customer_types(is_active);
CREATE INDEX IF NOT EXISTS idx_tax_profiles_rounding_strategy ON tax_profiles(rounding_strategy);
