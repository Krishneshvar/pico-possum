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

  // Migration
  detectLegacyDatabase: () => invoke('detect_legacy_database'),
  importLegacyDatabase: (filePath = null) =>
    invoke('import_legacy_database', { filePath }),
};
