-- Phase 1: Security Hardening Migration

-- Password history table for preventing password reuse
CREATE TABLE IF NOT EXISTS password_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  password_hash TEXT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_password_history_user_id ON password_history(user_id);
CREATE INDEX IF NOT EXISTS idx_password_history_created_at ON password_history(created_at);

-- Add password expiry tracking to users table
ALTER TABLE users ADD COLUMN password_changed_at DATETIME;
UPDATE users SET password_changed_at = CURRENT_TIMESTAMP WHERE password_changed_at IS NULL;
ALTER TABLE users ADD COLUMN password_expires_at DATETIME;

-- Login attempts tracking table
CREATE TABLE IF NOT EXISTS login_attempts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL,
  ip_address TEXT NOT NULL,
  success INTEGER NOT NULL CHECK(success IN (0,1)),
  failure_reason TEXT,
  attempted_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_username ON login_attempts(username);
CREATE INDEX IF NOT EXISTS idx_login_attempts_ip ON login_attempts(ip_address);
CREATE INDEX IF NOT EXISTS idx_login_attempts_attempted_at ON login_attempts(attempted_at);

-- Account lockout tracking
CREATE TABLE IF NOT EXISTS account_lockouts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  locked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  locked_until DATETIME NOT NULL,
  reason TEXT,
  locked_by INTEGER,
  unlocked_at DATETIME,
  unlocked_by INTEGER,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (locked_by) REFERENCES users(id),
  FOREIGN KEY (unlocked_by) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_account_lockouts_user_id ON account_lockouts(user_id);
CREATE INDEX IF NOT EXISTS idx_account_lockouts_locked_until ON account_lockouts(locked_until);

-- Enhanced audit log for security events
ALTER TABLE audit_log ADD COLUMN ip_address TEXT;
ALTER TABLE audit_log ADD COLUMN user_agent TEXT;
ALTER TABLE audit_log ADD COLUMN severity TEXT CHECK(severity IN ('info','warning','error','critical')) DEFAULT 'info';

CREATE INDEX IF NOT EXISTS idx_audit_log_severity ON audit_log(severity);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action);

-- Session enhancements for fingerprinting
CREATE TABLE IF NOT EXISTS sessions_new (
  id TEXT PRIMARY KEY,
  user_id INTEGER NOT NULL,
  token TEXT NOT NULL UNIQUE,
  expires_at INTEGER NOT NULL,
  data TEXT,
  ip_address TEXT,
  user_agent TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  last_activity_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Migrate existing sessions
INSERT INTO sessions_new (id, user_id, token, expires_at, data, created_at)
SELECT id, user_id, token, expires_at, data, created_at FROM sessions;

-- Drop old sessions table and rename new one
DROP TABLE sessions;
ALTER TABLE sessions_new RENAME TO sessions;

-- Recreate indexes
CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions(token);
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_last_activity ON sessions(last_activity_at);

-- Configurable security settings table
CREATE TABLE IF NOT EXISTS security_settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  description TEXT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by INTEGER,
  FOREIGN KEY (updated_by) REFERENCES users(id)
);

-- Insert default security settings
INSERT OR IGNORE INTO security_settings (key, value, description) VALUES
  ('password_min_length', '12', 'Minimum password length'),
  ('password_require_uppercase', '1', 'Require uppercase letters'),
  ('password_require_lowercase', '1', 'Require lowercase letters'),
  ('password_require_digits', '1', 'Require digits'),
  ('password_require_special', '1', 'Require special characters'),
  ('password_expiry_days', '90', 'Password expiration in days'),
  ('password_history_count', '5', 'Number of previous passwords to check'),
  ('max_login_attempts', '5', 'Maximum failed login attempts'),
  ('lockout_duration_minutes', '30', 'Account lockout duration'),
  ('session_timeout_minutes', '30', 'Session timeout duration'),
  ('max_sessions_per_user', '5', 'Maximum concurrent sessions per user');

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_customers_customer_type ON customers(customer_type);
CREATE INDEX IF NOT EXISTS idx_customers_is_tax_exempt ON customers(is_tax_exempt);
CREATE INDEX IF NOT EXISTS idx_users_password_expires_at ON users(password_expires_at);
