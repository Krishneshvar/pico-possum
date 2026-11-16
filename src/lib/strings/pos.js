/**
 * Strings for the Point of Sale workspace: the cart/checkout editor, the
 * add-customer modal, checkout toasts, confirmations and held-bill messages.
 *
 * Parameterised strings are functions. Interpolated money values are passed in
 * already formatted by the caller.
 */

const pos = {
  view: {
    saleCompletedTitle: 'Sale Completed',
    saleCompletedMessage: (invoice) => `Invoice ${invoice} completed successfully`,
    checkoutFailedTitle: 'Checkout Failed',
    saleFailed: (error) => `Sale failed: ${error}`,
    addItemsBeforeCheckout: 'Please add items to cart before completing checkout.',
    tenderRequiredTitle: 'Tender Amount Required',
    enterTenderedAmount: (amount) =>
      `Enter amount tendered (at least ${amount}) to complete sale.`,
    enterPartialPayment: (amount) =>
      `Enter partial payment amount (less than ${amount}) to complete sale.`,
    insufficientStockTitle: 'Insufficient Stock',
    insufficientStock: (name, available, cartQty) =>
      `Cannot complete sale: "${name}" has only ${available} unit${available === 1 ? '' : 's'} in stock (cart: ${cartQty}).`,
    outOfStock: (name) => `"${name}" is out of stock.`,
    stockLimitTitle: 'Stock Limit',
    stockLimit: (available, name) =>
      `Only ${available} unit${available === 1 ? '' : 's'} of "${name}" available in stock.`,
    updateCustomerFailed: (error) => `Failed to update customer: ${error}`,
    updateFailedTitle: 'Update failed',
    printDialogOpening: 'Opening system print dialog',
    billAlreadyEmpty: (billNumber) => `Bill ${billNumber} is already empty`,
    billCleared: (billNumber) => `Bill ${billNumber} cleared`,
    completeSale: 'Complete Sale [F12]',
    processingSale: 'Processing Sale...',
    invoiceNumber: 'Invoice Number:',
    changeToReturn: 'Change to Return',
    newSale: 'New Sale [Esc]',
    printReceipt: 'Print Receipt',
  },

  editor: {
    columns: {
      index: '#',
      qty: 'Qty',
      unitPrice: 'Unit Price',
      mrp: 'MRP',
      lineTotal: 'Line Total',
    },
    grandTotal: 'Grand Total',
    amountPlaceholder: '0.00',
    searchPlaceholder: 'Search... (Ctrl+K)',
    noMatchingProducts: (query) => `No products matching "${query}"`,
    skuNotFoundTitle: 'SKU Not Found',
    skuNotFound: (sku) => `No product found with SKU "${sku}"`,
    lineDiscount: (amount) => `Disc: -${amount}`,
    onlyInStock: (available) => `Only ${available} in stock`,
    priceExceedsMrpTitle: 'Price Exceeds MRP',
    priceExceedsMrp: (amount) => `Unit price cannot exceed MRP (${amount}).`,
    editLine: (name) => `Edit Line: ${name}`,
    unitRate: (symbol) => `Unit Rate (${symbol}):`,
    lineDiscountLabel: (symbol) => `Line Disc (${symbol}):`,
    lines: 'Lines:',
    totalUnits: 'Total Units:',
    cartSubtotal: 'Cart Subtotal:',
    changeCustomer: 'Change',
    addNewCustomer: '+ Add New',
    removeCustomer: 'Remove customer',
    phonePlaceholder: 'Phone number',
    addressNotesPlaceholder: 'Address / Notes',
    walkInCustomer: 'Walk-in Customer',
    paymentMethods: {
      cash: 'Cash',
      upi: 'UPI / QR',
      card: 'Card',
      giftCard: 'Gift Card',
    },
    exactAmountPayable: 'Exact amount payable',
    amountTendered: 'Amount Tendered',
    fullPayment: 'Full',
    partialPayment: 'Partial',
    changeToReturn: 'Change to return',
    balanceDue: 'Balance due',
    tenderAtLeast: (amount) => `Tender at least ${amount} to complete sale.`,
    remainingBalanceDue: 'Remaining balance due',
    partialPaymentHint: (amount) =>
      `Partial payment must be greater than zero and less than ${amount}.`,
    billDiscount: 'Bill Discount',
    flatDiscount: (symbol) => `${symbol} Flat`,
    notesPlaceholder: 'Internal remarks or delivery notes…',
    bills: 'Bills',
    clearCart: 'Clear Cart',
  },

  customerModal: {
    title: 'Add New Customer',
    nameLabel: 'Customer Name',
    namePlaceholder: 'e.g. John Doe',
    phoneLabel: 'Phone Number',
    phonePlaceholder: 'e.g. 9876543210',
    addressLabel: 'Address / Notes',
    addressPlaceholder: 'Street / Locality / GSTIN',
    nameRequired: 'Customer name is required',
    attachedToBill: (name) => `Customer "${name}" attached to bill`,
    saveFailedTitle: 'Save failed',
    addFailed: (error) => `Failed to add customer: ${error}`,
    saveCustomer: 'Save Customer',
  },

  toasts: {
    partialPaymentLimitTitle: 'Partial Payment Limit',
    partialPaymentLimit: (amount) =>
      `Partial payment cannot be equal to or greater than the bill amount (${amount}).`,
  },

  confirms: {
    clearCart: {
      title: 'Clear Cart',
      message: (count, billNumber) =>
        `Remove all ${count} items from Bill ${billNumber}?`,
      confirmLabel: 'Clear Cart',
      cancelLabel: 'Keep Items',
    },
  },

  store: {
    restoreFailed: 'Open bills could not be restored from the database',
    restoreFailedTitle: 'Session restore failed',
  },
};

export default pos;
