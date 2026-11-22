/**
 * Strings for the thermal receipt (ReceiptPrinter).
 *
 * Receipt-specific labels, table columns, fallbacks and status banners.
 * Generic labels (`Date`, `Time`, `Subtotal`, `Discount`, `Total`) are reused
 * from `strings.common.labels` at the call site.
 */

const receipt = {
  labels: {
    billNo: 'Bill No',
    cust: 'Cust',
    cashier: 'Cashier',
    taxAmount: 'Tax Amount',
    change: 'Change',
    balanceDue: 'Balance Due',
    phoneLine: (phone) => `Ph: ${phone}`,
    gstinLine: (gstin) => `GSTIN: ${gstin}`,
    paidVia: (method) => `Paid via (${method}):`,
  },

  columns: {
    item: 'Item',
    qty: 'Qty',
    rate: 'Rate',
    tax: 'Tax%',
    amount: 'Amt',
    taxRate: (rate) => `${rate}%`,
  },

  defaults: {
    storeName: 'STORE NAME',
    cashCustomer: 'Walk-in Customer',
    footerText: 'Thank you for your visit!',
  },

  banners: {
    cancelled: '*** CANCELLED / VOID ***',
    amended: '* AMENDED INVOICE *',
  },
};

export default receipt;
