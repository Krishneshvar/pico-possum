/** Strings for the products area. */

const products = {
  view: {
    title: 'Product Catalog',
    subtitle: 'Manage items, rates, categories, and inventory',
    addProduct: 'Add Product',
    emptyMessage: 'No products found in catalog.',
  },

  actions: {
    adjustStock: 'Adjust Stock',
    editProduct: 'Edit Product',
    deleteProduct: 'Delete Product',
  },

  columns: {
    skuBarcode: 'SKU / Barcode',
    costPrice: 'Cost Price',
    mrp: 'MRP',
    gst: 'GST %',
    stockLevel: 'Stock Level',
    lowStockTooltip: (threshold) => `Low stock alert (≤ ${threshold})`,
    outOfStockTooltip: 'Out of stock',
    stockTooltip: (stock) => `Stock: ${stock}`,
  },

  filters: {
    categories: 'Categories',
    stockLevel: 'Stock Level',
    status: 'Status',
    price: 'Price',
    inStock: (count) => `In stock (${count})`,
    lowStock: (count) => `Low stock (${count})`,
    outOfStock: (count) => `Out of stock (${count})`,
    active: (count) => `Active (${count})`,
    inactive: (count) => `Inactive (${count})`,
    discontinued: (count) => `Discontinued (${count})`,
  },

  /** Display labels for the product status enum values. */
  statusLabels: {
    active: 'active',
    inactive: 'inactive',
    discontinued: 'discontinued',
  },

  form: {
    createTitle: 'Add New Product',
    sections: {
      productInfo: 'Product Information',
      pricing: 'Pricing & Taxation',
      inventory: 'Inventory Configuration',
    },
    productName: 'Product Name',
    skuBarcode: 'SKU / Barcode',
    autoBadge: 'Auto',
    category: 'Category',
    noCategory: 'No Category',
    costPrice: (currencySymbol) => `Cost Price (${currencySymbol})`,
    mrp: (currencySymbol) => `MRP (${currencySymbol})`,
    gstTaxRate: 'GST Tax Rate (%)',
    lowStockAlert: 'Low-Stock Alert Threshold',
    productStatus: 'Product Status',
    initialStock: 'Initial Opening Stock (Units)',
    initialStockHelp:
      'Recorded as initial stock balance in the stock movements ledger.',
    createSubmit: 'Create Product',
    statusOptions: {
      active: 'Active (Available for POS)',
      inactive: 'Inactive (Hidden)',
      discontinued: 'Discontinued',
    },
    placeholders: {
      productName: 'e.g. Fresh Milk 500ml',
      generatingSku: 'Generating SKU…',
      sku: 'e.g. 100201',
      zeroDecimal: '0.00',
      mrp: '100.00',
      zero: '0',
      five: '5',
    },
  },

  stock: {
    currentBalance: 'Current Balance:',
    units: (count) => `${count} units`,
    projectedUnits: (count) => `→ ${count} units`,
    reason: 'Stock Change Reason',
    negativeHint: 'Negative stock → Manual Correction only',
    manualCorrection: 'Manual Stock Correction',
    purchase: 'Purchase',
    purchaseDisabledTooltip: 'Cannot select Purchase for negative stock deductions',
    purchaseTooltip: 'Add purchased stock from supplier',
    quantityChange: 'Quantity Change (+ to add, − to deduct)',
    noteLabel: 'Reason / Note',
    addOne: 'Add 1',
    deductOne: 'Deduct 1',
    purchaseNegativeWarning:
      'Purchase reason cannot be used for negative stock deductions',
    quantityRequired:
      'Enter a non-zero quantity to adjust stock (e.g. +10 or -5)',
    fallbackProductName: 'product',
    placeholders: {
      quantity: 'e.g. +10 or -5',
      note: 'e.g. Supplier challan #882, count discrepancy, damaged',
    },
  },

  dialog: {
    deleteTitle: (name) => `Delete "${name}"?`,
    deleteMessage: (name, sku) =>
      `Are you sure you want to delete "${name}" (SKU: ${sku})? This item will be removed from the active catalog.`,
    deleteConfirm: 'Delete Product',
  },

  validation: {
    nameRequired: 'Product name is required',
  },

  toasts: {
    created: (name) => `Product "${name}" added to catalog`,
    createFailedTitle: 'Create failed',
    createFailed: (error) => `Failed to create product: ${error}`,
    updated: (name) => `Product "${name}" updated successfully`,
    updateFailedTitle: 'Update failed',
    updateFailed: (error) => `Failed to update product: ${error}`,
    deleted: 'Product removed from active catalog',
    deleteFailedTitle: 'Delete failed',
    deleteFailed: (error) => `Failed to delete product: ${error}`,
    stockAdjusted: (delta, name) =>
      `${delta > 0 ? 'Added' : 'Removed'} ${Math.abs(delta)} unit${
        Math.abs(delta) === 1 ? '' : 's'
      } ${delta > 0 ? 'to' : 'from'} "${name}"`,
    stockUpdateFailedTitle: 'Stock update failed',
    stockUpdateFailed: (error) => `Failed to adjust stock: ${error}`,
  },

  export: {
    noProducts: 'No products to export',
    uncategorized: 'Uncategorized',
    sheetName: 'Products',
    headers: {
      gst: 'GST (%)',
      currentStock: 'Current Stock',
      minStockAlert: 'Min Stock Alert',
    },
  },
};

export default products;
