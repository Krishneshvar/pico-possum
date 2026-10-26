import { invoke } from '@tauri-apps/api/core';

export const tauriApi = {
  // Products
  getProducts: (categoryId = null, search = null) =>
    invoke('get_products', { categoryId, search }),
  getProductById: (id) => invoke('get_product_by_id', { id }),
  createProduct: (payload) => invoke('create_product', { payload }),
  updateProduct: (id, payload) => invoke('update_product', { id, payload }),
  deleteProduct: (id) => invoke('delete_product', { id }),
  adjustStock: (payload) => invoke('adjust_stock', { payload }),

  // Categories
  getCategories: () => invoke('get_categories'),
  createCategory: (payload) => invoke('create_category', { payload }),
  updateCategory: (id, payload) => invoke('update_category', { id, payload }),
  deleteCategory: (id) => invoke('delete_category', { id }),

  // Sales & POS
  processSale: (payload) => invoke('process_sale', { payload }),
  getSales: (limit = 50, offset = 0, query = null) =>
    invoke('get_sales', { limit, offset, query }),
  getSaleDetails: (saleId) => invoke('get_sale_details', { saleId }),
  cancelSale: (saleId, reason) => invoke('cancel_sale', { saleId, reason }),
  updateSale: (payload) => invoke('update_sale', { payload }),
  saveOpenBill: (billIndex, payload) => invoke('save_open_bill', { billIndex, payload }),
  loadOpenBills: () => invoke('load_open_bills'),
  deleteOpenBill: (billIndex) => invoke('delete_open_bill', { billIndex }),

  // Customers
  getCustomers: (search = null) => invoke('get_customers', { search }),
  createCustomer: (payload) => invoke('create_customer', { payload }),
  updateCustomer: (id, payload) => invoke('update_customer', { id, payload }),
  deleteCustomer: (id) => invoke('delete_customer', { id }),
  getCustomerSales: (customerId) => invoke('get_customer_sales', { customerId }),

  // Reports
  getSalesReportSummary: (fromDate = null, toDate = null) =>
    invoke('get_sales_report_summary', { fromDate, toDate }),
  getSalesBreakdown: (periodType = 'daily', fromDate = null, toDate = null) =>
    invoke('get_sales_breakdown', { periodType, fromDate, toDate }),
  getProductFlow: (productId = null, fromDate = null, toDate = null, limit = 200) =>
    invoke('get_product_flow', { productId, fromDate, toDate, limit }),
  getTopSellingProducts: (limit = 10) =>
    invoke('get_top_selling_products', { limit }),
  getStockMovements: (productId = null, fromDate = null, toDate = null, limit = 200) =>
    invoke('get_stock_movements', { productId, fromDate, toDate, limit }),
  getAuditLogs: (limit = 100) => invoke('get_audit_logs', { limit }),

  // Settings
  getGeneralSettings: () => invoke('get_general_settings'),
  updateGeneralSettings: (settings) => invoke('update_general_settings', { settings }),
  getBillSettings: () => invoke('get_bill_settings'),
  updateBillSettings: (settings) => invoke('update_bill_settings', { settings }),

  // Hardware & Printing
  listPrinters: () => invoke('list_printers'),
  testPrint: (printerName = null) => invoke('test_print', { printerName }),

  // Backup & Restore
  getBackupStatus: () => invoke('get_backup_status'),
  listBackups: () => invoke('list_backups'),
  createManualBackup: () => invoke('create_manual_backup'),
  restoreBackup: (backupPath) => invoke('restore_backup', { backupPath }),
  restoreBackupFile: (filename, bytes) => invoke('restore_backup_file', { filename, bytes }),
  openBackupFolder: () => invoke('open_backup_folder'),


  // Migration
  detectLegacyDatabase: () => invoke('detect_legacy_database'),
  importLegacyDatabase: (filePath = null) =>
    invoke('import_legacy_database', { filePath }),

  // CSV Imports
  importCsvCategories: ({ csvContent, filePath }) =>
    invoke('import_csv_categories', { csvContent, filePath }),
  importCsvProducts: ({ csvContent, filePath }) =>
    invoke('import_csv_products', { csvContent, filePath }),
  importCsvCustomers: ({ csvContent, filePath }) =>
    invoke('import_csv_customers', { csvContent, filePath }),
  importCsvBills: ({ csvContent, filePath, fileName }) =>
    invoke('import_csv_bills', { csvContent, filePath, fileName }),

  // Auth & Identity
  getAuthState: () => invoke('get_auth_state'),
  getAdminUsername: () => invoke('get_admin_username'),
  registerAdmin: ({ name, username, password }) =>
    invoke('register_admin', { req: { name, username, password } }),
  login: ({ username, password }) =>
    invoke('login', { req: { username, password } }),
  logout: () => invoke('logout'),
  updatePassword: ({ currentPassword, newPassword }) =>
    invoke('update_password', {
      req: {
        current_password: currentPassword,
        new_password: newPassword,
      },
    }),
};

