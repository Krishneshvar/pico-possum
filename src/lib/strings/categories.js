/** Strings for the categories area. */

const categories = {
  view: {
    title: 'Categories Hierarchy',
    subtitle: 'Organize catalog hierarchy and class mappings',
    addCategory: 'Add Category',
    viewModes: {
      split: 'Split',
      splitTitle: 'Split View (Tree + Table)',
      tree: 'Tree',
      treeTitle: 'Tree View',
      table: 'Table',
      tableTitle: 'Table View',
    },
    treePaneTitle: 'Category Tree',
    clearSelection: 'Clear Selection',
    allCategories: 'All Categories',
    emptyTree: 'No categories created yet. Click "Add Category" above.',
    resizePane: 'Drag to resize pane',
    filteringFor: 'Filtering category and subcategories for:',
    showAll: 'Show all',
    emptyTable: 'No categories found.',
  },

  columns: {
    name: 'Category Name',
    parent: 'Parent Category',
    products: 'Products',
  },

  actions: {
    addSubcategory: 'Add Subcategory',
    editCategory: 'Edit Category',
    deleteCategory: 'Delete Category',
  },

  tree: {
    addSubcategory: 'Add subcategory',
    editCategory: 'Edit category',
    deleteCategory: 'Delete category',
  },

  form: {
    createTitle: 'Create Category',
    editTitle: 'Edit Category',
    nameLabel: 'Category Name',
    namePlaceholder: 'e.g. Beverages, Dairy, Snacks',
    parentLabel: 'Parent Category (Optional)',
    noneTopLevel: 'None (Top-Level Category)',
    parentHint: 'Nest this category under an existing parent class',
  },

  dialogs: {
    deleteTitle: (name) => `Delete Category "${name}"?`,
    deleteMessage: (name) =>
      `Are you sure you want to delete category "${name}"? This action cannot be undone.`,
    deleteConfirm: 'Delete Category',
    deleteConfirmBtn: 'Delete Category',
    deleteCancelBtn: 'Cancel',
    deleteWithChildrenAndProducts: (name, childCount, productCount) =>
      `Category "${name}" has ${childCount} subcategory and ${productCount} products. Deleting it will un-nest its subcategories and remove category association from those products. Are you sure?`,
    deleteWithChildren: (name, childCount) =>
      `Category "${name}" has ${childCount} subcategory. Deleting it will promote subcategories to top-level. Are you sure?`,
    deleteWithProducts: (name, productCount) =>
      `Category "${name}" has ${productCount} assigned products. Are you sure you want to delete this category?`,
  },

  toasts: {
    created: 'Category created successfully',
    createFailed: (error) => `Failed to create category: ${error}`,
    createFailedTitle: 'Create failed',
    updated: 'Category updated successfully',
    updateFailed: (error) => `Failed to update category: ${error}`,
    updateFailedTitle: 'Update failed',
    deleted: 'Category deleted successfully',
    deleteFailed: (error) => `Failed to delete category: ${error}`,
    deleteFailedTitle: 'Delete failed',
    noCategoriesToExport: 'No categories to export',
    deleteHasProducts: (name, count) =>
      `Cannot delete "${name}" because it still contains ${count} product${
        count === 1 ? '' : 's'
      }. Reassign those products first.`,
    nameRequired: 'Category name is required',
  },

  export: {
    entityName: 'categories',
    sheetName: 'Categories',
    headers: {
      name: 'Category Name',
      parent: 'Parent Category',
      productCount: 'Product Count',
    },
  },

  stockLedger: {
    title: 'Stock Activity Ledger',
    subtitle: 'Inventory movements, restocking history, audits, and sales ledger',
    refresh: 'Refresh Ledger',
    empty: 'No stock movements found for the selected period.',
    columns: {
      dateTime: 'Date & Time',
      sku: 'SKU',
      product: 'Product',
      reason: 'Stock Change Reason',
      quantityDelta: 'Quantity Delta',
      reference: 'Reference / Invoice',
      notes: 'Audit Reason / Notes',
    },
    unknownItem: 'Unknown Item',
    openBill: (invoiceNumber) => `Open bill ${invoiceNumber}`,
    billTabTitle: (invoiceNumber) => `Bill #${invoiceNumber}`,
    filters: {
      reasons: 'Stock Change Reasons',
      date: 'Date Filter',
    },
    movementTypes: {
      manualStockCorrection: 'Manual Stock Correction',
      purchase: 'Purchase',
      billAdjustment: 'Bill Adjustment',
      return: 'Return',
      cancelled: 'Cancelled',
      sale: 'Sale',
      productDeleted: 'Product Deleted',
    },
  },
};

export default categories;
