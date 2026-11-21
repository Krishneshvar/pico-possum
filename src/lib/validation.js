/**
 * Centralized Validation Library for PicoPOSSUM
 * Defines validation rules and form evaluators for products, categories,
 * customers, stock adjustments, pricing, and user security.
 */

export const VALIDATION_LIMITS = {
  PRODUCT_NAME_MAX: 200,
  SKU_MAX: 50,
  CATEGORY_NAME_MAX: 100,
  CUSTOMER_NAME_MAX: 150,
  CUSTOMER_PHONE_MAX: 30,
  CUSTOMER_ADDRESS_MAX: 500,
  NOTE_MAX: 500,
  PASSWORD_MIN: 4,
  PASSWORD_MAX: 128,
  TAX_RATE_MIN: 0,
  TAX_RATE_MAX: 100,
};

const PHONE_REGEX = /^[0-9+\-()\s/]*$/;

// ==========================================
// Individual Field Validators
// ==========================================

export function validateProductName(name) {
  if (!name || !name.trim()) {
    return 'Product name is required';
  }
  if (name.trim().length > VALIDATION_LIMITS.PRODUCT_NAME_MAX) {
    return `Product name cannot exceed ${VALIDATION_LIMITS.PRODUCT_NAME_MAX} characters`;
  }
  return null;
}

export function validateProductSku(sku) {
  if (sku && sku.trim().length > VALIDATION_LIMITS.SKU_MAX) {
    return `SKU cannot exceed ${VALIDATION_LIMITS.SKU_MAX} characters`;
  }
  return null;
}

export function validateMrp(mrp) {
  if (mrp === '' || mrp == null) {
    return 'MRP is required';
  }
  const parsed = Number(mrp);
  if (isNaN(parsed) || parsed < 0) {
    return 'MRP must be a valid non-negative number';
  }
  return null;
}

export function validateCostPrice(costPrice) {
  if (costPrice === '' || costPrice == null) return null;
  const parsed = Number(costPrice);
  if (isNaN(parsed) || parsed < 0) {
    return 'Cost price must be a valid non-negative number';
  }
  return null;
}

export function validateTaxRate(taxRate) {
  if (taxRate === '' || taxRate == null) return null;
  const parsed = Number(taxRate);
  if (isNaN(parsed) || parsed < VALIDATION_LIMITS.TAX_RATE_MIN || parsed > VALIDATION_LIMITS.TAX_RATE_MAX) {
    return `Tax rate must be between ${VALIDATION_LIMITS.TAX_RATE_MIN}% and ${VALIDATION_LIMITS.TAX_RATE_MAX}%`;
  }
  return null;
}

export function validateStock(stock) {
  if (stock === '' || stock == null) return null;
  const parsed = parseInt(stock, 10);
  if (isNaN(parsed) || parsed < 0) {
    return 'Initial stock must be a non-negative whole number';
  }
  return null;
}

export function validateMinStockAlert(alert) {
  if (alert === '' || alert == null) return null;
  const parsed = parseInt(alert, 10);
  if (isNaN(parsed) || parsed < 0) {
    return 'Minimum stock alert must be a non-negative whole number';
  }
  return null;
}

export function validateCategoryName(name) {
  if (!name || !name.trim()) {
    return 'Category name is required';
  }
  if (name.trim().length > VALIDATION_LIMITS.CATEGORY_NAME_MAX) {
    return `Category name cannot exceed ${VALIDATION_LIMITS.CATEGORY_NAME_MAX} characters`;
  }
  return null;
}

export function validateCategoryParent(currentCategoryId, parentId) {
  if (parentId && currentCategoryId && String(parentId) === String(currentCategoryId)) {
    return 'A category cannot be its own parent';
  }
  return null;
}

export function validateCustomerName(name) {
  if (!name || !name.trim()) {
    return 'Customer name is required';
  }
  if (name.trim().length > VALIDATION_LIMITS.CUSTOMER_NAME_MAX) {
    return `Customer name cannot exceed ${VALIDATION_LIMITS.CUSTOMER_NAME_MAX} characters`;
  }
  return null;
}

export function validateCustomerPhone(phone) {
  if (!phone || !phone.trim()) return null;
  const trimmed = phone.trim();
  if (trimmed.length > VALIDATION_LIMITS.CUSTOMER_PHONE_MAX) {
    return `Phone number cannot exceed ${VALIDATION_LIMITS.CUSTOMER_PHONE_MAX} characters`;
  }
  if (!PHONE_REGEX.test(trimmed)) {
    return 'Phone number contains invalid characters';
  }
  return null;
}

export function validateCustomerAddress(address) {
  if (address && address.trim().length > VALIDATION_LIMITS.CUSTOMER_ADDRESS_MAX) {
    return `Address cannot exceed ${VALIDATION_LIMITS.CUSTOMER_ADDRESS_MAX} characters`;
  }
  return null;
}

export function validateUnitPriceAgainstMrp(unitPricePaise, mrpPaise, productName = '') {
  if (unitPricePaise != null && mrpPaise != null && unitPricePaise > mrpPaise) {
    const target = productName ? ` for "${productName}"` : '';
    return `Unit price cannot exceed MRP${target}`;
  }
  return null;
}

export function validateStockAdjustment(quantityChange, note, reason, currentStock = 0, allowNegativeStock = false) {
  const errors = {};
  const parsedQty = parseInt(quantityChange, 10);

  if (isNaN(parsedQty) || parsedQty === 0) {
    errors.quantityChange = 'Enter a non-zero quantity change (e.g. +5 or -2)';
  } else if (!allowNegativeStock && currentStock + parsedQty < 0) {
    errors.quantityChange = `Adjustment results in negative stock (${currentStock + parsedQty}), which is disabled in settings`;
  }

  if (parsedQty < 0 && reason === 'PURCHASE') {
    errors.reason = 'Cannot select Purchase reason for a negative stock deduction';
  }

  if (note && note.trim().length > VALIDATION_LIMITS.NOTE_MAX) {
    errors.note = `Note cannot exceed ${VALIDATION_LIMITS.NOTE_MAX} characters`;
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

export function validatePasswordChange(currentPassword, newPassword, confirmPassword) {
  const errors = {};

  if (!currentPassword || !currentPassword.trim()) {
    errors.currentPassword = 'Current password is required';
  }

  if (!newPassword || newPassword.length < VALIDATION_LIMITS.PASSWORD_MIN) {
    errors.newPassword = `New password must be at least ${VALIDATION_LIMITS.PASSWORD_MIN} characters long`;
  } else if (newPassword.length > VALIDATION_LIMITS.PASSWORD_MAX) {
    errors.newPassword = `New password cannot exceed ${VALIDATION_LIMITS.PASSWORD_MAX} characters`;
  }

  if (confirmPassword !== undefined && newPassword !== confirmPassword) {
    errors.confirmPassword = 'Passwords do not match';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

// ==========================================
// Full Form Validators
// ==========================================

export function validateProductForm(formData, isCreate = false) {
  const errors = {};

  const nameErr = validateProductName(formData.name);
  if (nameErr) errors.name = nameErr;

  const skuErr = validateProductSku(formData.sku);
  if (skuErr) errors.sku = skuErr;

  const mrpVal = formData.mrpRupees ?? formData.mrp_rupees;
  const mrpErr = validateMrp(mrpVal);
  if (mrpErr) errors.mrpRupees = mrpErr;

  const cpVal = formData.costPriceRupees ?? formData.cost_price_rupees;
  const cpErr = validateCostPrice(cpVal);
  if (cpErr) errors.costPriceRupees = cpErr;

  const taxVal = formData.taxRate ?? formData.tax_rate;
  const taxErr = validateTaxRate(taxVal);
  if (taxErr) errors.taxRate = taxErr;

  if (isCreate) {
    const stockVal = formData.initialStock ?? formData.initial_stock;
    const stockErr = validateStock(stockVal);
    if (stockErr) errors.initialStock = stockErr;
  }

  const alertVal = formData.minStockAlert ?? formData.min_stock_alert;
  const alertErr = validateMinStockAlert(alertVal);
  if (alertErr) errors.minStockAlert = alertErr;

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

export function validateCategoryForm(name, parentId, editingCategoryId = null) {
  const errors = {};

  const nameErr = validateCategoryName(name);
  if (nameErr) errors.name = nameErr;

  const parentErr = validateCategoryParent(editingCategoryId, parentId);
  if (parentErr) errors.parentId = parentErr;

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

export function validateCustomerForm(formData) {
  const errors = {};

  const nameErr = validateCustomerName(formData.name);
  if (nameErr) errors.name = nameErr;

  const phoneErr = validateCustomerPhone(formData.phone);
  if (phoneErr) errors.phone = phoneErr;

  const addrErr = validateCustomerAddress(formData.address);
  if (addrErr) errors.address = addrErr;

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}
