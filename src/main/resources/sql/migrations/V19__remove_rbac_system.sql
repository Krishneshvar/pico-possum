-- V18__remove_rbac_system.sql

-- 1. Remove security related tables
DROP TABLE IF EXISTS login_attempts;
DROP TABLE IF EXISTS account_lockouts;
DROP TABLE IF EXISTS password_history;
DROP TABLE IF EXISTS security_settings;
DROP TABLE IF EXISTS permission_delegations;
DROP TABLE IF EXISTS time_based_permissions;
DROP TABLE IF EXISTS role_hierarchy;
DROP TABLE IF EXISTS user_permissions;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS sessions;
-- 2. Cleanup users table
DELETE FROM users WHERE id != 1;

-- 3. Ensure default admin user exists
INSERT OR IGNORE INTO users (id, name, username, password_hash, is_active)
VALUES (1, 'System Administrator', 'admin', 'password', 1);

-- 4. Simplified audit log severity
UPDATE audit_log SET severity = 'info' WHERE severity NOT IN ('info', 'warning', 'error');
