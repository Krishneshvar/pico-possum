/** Strings for the bills area. */

const bills = {
  labels: {
    walkInCustomer: 'Walk-in Customer',
    itemFallback: 'item',
  },

  history: {
    title: 'Bill History',
    subtitle: 'Search, reprint receipts, inspect audits, and manage past sales',
    tabTitle: (invoiceNumber) => `Bill #${invoiceNumber}`,
  },

  columns: {
    index: '#',
    payment: 'Payment',
    totalAmount: 'Total Amount',
    productName: 'Product Name',
    unitRate: 'Unit Rate',
    quantity: 'Quantity',
    discount: 'Discount',
    taxPercent: 'Tax %',
    total: 'Total',
  },

  filters: {
    statusOptions: {
      paid: (count) => `Paid (${count})`,
      legacy: (count) => `Legacy (${count})`,
      cancelled: (count) => `Cancelled (${count})`,
    },
    paymentOptions: {
      cash: (count) => `Cash (${count})`,
      upi: (count) => `UPI / QR (${count})`,
      card: (count) => `Card (${count})`,
      giftCard: (count) => `Gift Card (${count})`,
    },
  },

  status: {
    cancelled: 'Cancelled',
    legacy: 'Legacy',
    paid: 'Paid',
  },

  actions: {
    viewDetails: 'View Details',
    reprintReceipt: 'Reprint Receipt',
    cancelInvoiceReturnStock: 'Cancel Invoice & Return Stock',
    amendBill: 'Amend Bill',
    cancelReturn: 'Cancel / Return',
    discard: 'Discard',
    save: 'Save',
    saving: 'Saving…',
    savingChanges: 'Saving Changes…',
    returnToHistory: 'Return to Bill History',
  },

  detail: {
    loading: 'Loading bill details…',
    notFound: (id) => `Could not find bill details for ID: ${id}`,
    heading: (invoiceNumber) => `Bill #${invoiceNumber}`,
    editingMode: 'Editing Mode',
    discardEdit: 'Discard edit',
    backToHistory: 'Back to Bill History',
    cancelledBanner: {
      title: 'This bill has been cancelled',
      message: 'Items were returned to inventory and transaction recorded as voided.',
    },
    items: {
      heading: 'Itemized Bill',
      lineCount: (count) => `${count} line${count !== 1 ? 's' : ''}`,
      totalUnits: (count) => `${count} total units`,
    },
    customer: {
      heading: 'Customer Details',
      noPhone: 'No phone provided',
    },
    payment: {
      heading: 'Payment & Transaction',
      cashier: 'Cashier / Operator',
      operatorDefault: 'Counter Terminal',
    },
    summary: {
      heading: 'Financial Summary',
      gst: 'GST Tax',
      grandTotal: 'Grand Total',
      paidAmount: (method) => `Paid Amount (${method})`,
      changeReturned: 'Change Returned',
      balanceDue: 'Balance Due',
      balanceStatus: 'Balance Status',
      settled: 'Settled',
      notesLabel: 'Transaction Notes:',
    },
  },

  dialogs: {
    cancelInvoice: {
      title: (invoiceNumber) => `Cancel Invoice ${invoiceNumber || ''}?`,
      message: (invoiceNumber) =>
        `Are you sure you want to cancel invoice #${invoiceNumber}? This will mark the transaction as CANCELLED and restore the items to inventory.`,
      reasonLabel: 'Reason for Cancellation / Return',
      reasonDefault: 'Customer returned items / cancelled',
      reasonPlaceholder: 'Reason for cancellation',
      confirm: 'Cancel Invoice',
      cancel: 'Keep Active',
    },
    cancelBill: {
      title: (invoiceNumber) => `Cancel Bill #${invoiceNumber}?`,
      message: (invoiceNumber) =>
        `Are you sure you want to cancel invoice #${invoiceNumber}? All items will be returned to inventory.`,
      reasonLabel: 'Cancellation Reason',
      reasonDefault: 'Customer return / refund',
      reasonPlaceholder: 'Reason for return',
      confirm: 'Cancel Bill',
      cancel: 'Keep Active',
    },
    amend: {
      title: 'Confirm Bill Amendment',
      message: (invoiceNumber) =>
        `Save changes to invoice #${invoiceNumber}? A structured amendment record will be logged.`,
      reasonLabel: 'Amendment Reason (Audit Trail)',
      reasonDefault: 'Correction of line item / rate amendment',
      reasonPlaceholder: 'Reason for change',
      confirm: 'Save Changes',
      cancel: 'Keep Editing',
    },
  },

  validation: {
    emptyBill: 'A bill must have at least one item.',
    fullPaymentRequired: (amount) =>
      `Full payment requires an amount tendered equal to or greater than the bill amount (${amount}).`,
    partialPaymentRequired:
      'Partial payment requires an amount tendered greater than zero.',
    partialPaymentTooHigh: (amount) =>
      `Partial payment cannot be equal to or greater than the bill amount (${amount}).`,
  },

  toasts: {
    saleCancelled: 'Sale marked as cancelled and inventory restored',
    saleCancelledReturned: 'Sale cancelled and items returned to stock',
    cancelFailed: (error) => `Failed to cancel sale: ${error}`,
    cancelFailedTitle: 'Cancellation failed',
    receiptPreparing: 'Preparing receipt for printing',
    receiptLoadFailed: (error) => `Failed to load receipt: ${error}`,
    amended: 'Bill amended and inventory adjustments logged',
    saveFailed: (error) => `Save failed: ${error}`,
    saveFailedTitle: 'Update failed',
    outOfStock: (name) => `"${name}" is out of stock.`,
    stockLimit: (available, name) =>
      `Only ${available} unit${available === 1 ? '' : 's'} of "${name}" available in stock.`,
    stockLimitTitle: 'Stock limit reached',
    noBillsToExport: 'No bills to export',
  },

  export: {
    headers: {
      customerName: 'Customer Name',
      customerPhone: 'Customer Phone',
      subtotal: (symbol) => `Subtotal (${symbol})`,
      discount: (symbol) => `Discount (${symbol})`,
      tax: (symbol) => `Tax (${symbol})`,
      netTotal: (symbol) => `Net Total (${symbol})`,
    },
    sheetName: 'Bills',
    entityName: 'bills',
  },
};

export default bills;
