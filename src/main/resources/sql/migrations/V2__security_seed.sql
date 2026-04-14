-- Default Roles
INSERT OR IGNORE INTO roles (name, description) VALUES ('admin', 'Full system access with all permissions');
INSERT OR IGNORE INTO roles (name, description) VALUES ('manager', 'Manages operations, inventory, and reports');
INSERT OR IGNORE INTO roles (name, description) VALUES ('cashier', 'Basic sales and customer operations');

-- Default Permissions
INSERT OR IGNORE INTO permissions (key) VALUES ('users.view');
INSERT OR IGNORE INTO permissions (key) VALUES ('users.manage');
INSERT OR IGNORE INTO permissions (key) VALUES ('products.view');
INSERT OR IGNORE INTO permissions (key) VALUES ('products.manage');
INSERT OR IGNORE INTO permissions (key) VALUES ('inventory.view');
INSERT OR IGNORE INTO permissions (key) VALUES ('inventory.manage');
INSERT OR IGNORE INTO permissions (key) VALUES ('sales.view');
INSERT OR IGNORE INTO permissions (key) VALUES ('sales.create');
INSERT OR IGNORE INTO permissions (key) VALUES ('sales.manage');
INSERT OR IGNORE INTO permissions (key) VALUES ('sales.refund');
INSERT OR IGNORE INTO permissions (key) VALUES ('reports.view');
INSERT OR IGNORE INTO permissions (key) VALUES ('suppliers.view');
INSERT OR IGNORE INTO permissions (key) VALUES ('suppliers.manage');
INSERT OR IGNORE INTO permissions (key) VALUES ('purchase.view');
INSERT OR IGNORE INTO permissions (key) VALUES ('purchase.manage');
INSERT OR IGNORE INTO permissions (key) VALUES ('customers.view');
INSERT OR IGNORE INTO permissions (key) VALUES ('customers.manage');
INSERT OR IGNORE INTO permissions (key) VALUES ('categories.view');
INSERT OR IGNORE INTO permissions (key) VALUES ('categories.manage');
INSERT OR IGNORE INTO permissions (key) VALUES ('settings.view');
INSERT OR IGNORE INTO permissions (key) VALUES ('settings.manage');
INSERT OR IGNORE INTO permissions (key) VALUES ('audit.view');
INSERT OR IGNORE INTO permissions (key) VALUES ('returns.view');
INSERT OR IGNORE INTO permissions (key) VALUES ('returns.manage');
INSERT OR IGNORE INTO permissions (key) VALUES ('transactions.view');

-- Assign Permissions to Admin
INSERT OR IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'admin';

-- Assign Permissions to Manager
INSERT OR IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p 
WHERE r.name = 'manager' AND p.key IN (
    'products.view', 'products.manage', 
    'inventory.view', 'inventory.manage',
    'sales.view', 'sales.create', 'sales.manage', 'sales.refund',
    'reports.view', 'suppliers.view', 'suppliers.manage', 
    'purchase.view', 'purchase.manage',
    'customers.view', 'customers.manage', 
    'categories.view', 'categories.manage',
    'settings.view', 'settings.manage',
    'returns.view', 'returns.manage',
    'transactions.view'
);

-- Assign Permissions to Cashier
INSERT OR IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p 
WHERE r.name = 'cashier' AND p.key IN (
    'products.view', 'inventory.view',
    'sales.view', 'sales.create', 
    'customers.view', 'categories.view',
    'transactions.view'
);
