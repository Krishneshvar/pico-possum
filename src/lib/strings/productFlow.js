/**
 * Strings for the insights screens backed by this module: Product Flow and the
 * System Audit Log. Generic wording (actions, field labels, states) is pulled
 * from `common` where the same wording is reused elsewhere.
 */

const productFlow = {
  productFlow: {
    title: 'Product Flow',
    subtitle:
      'Track historical checkout velocity, price movement, and inventory sales flow',
    refreshTitle: 'Refresh Product Flow',
    selectProduct: 'Select Product:',
    selectProductPlaceholder: '-- Choose a product to inspect history --',
    productOption: (name, sku) => `${name}${sku ? ` (SKU: ${sku})` : ''}`,
    skuBadge: (sku) => `SKU: ${sku}`,
    summarySubtitle: 'Transaction audit and historical customer sales records',
    totalSold: 'Total Sold:',
    totalRevenue: 'Total Revenue:',
    avgSoldPrice: 'Avg Sold Price:',
    mrp: 'MRP:',
    currentStock: 'Current Stock:',
    units: (count) => `${count} units`,
    transactionRecords: 'Transaction Records',
    emptyTable: 'No transaction events found for this product in the selected period.',
    noProductTitle: 'No Product Selected',
    noProductDescription:
      'Please choose a product from the dropdown above to inspect its transaction history, sales velocity, and average selling price.',
    viewBillTitle: (invoiceNumber) => `View bill #${invoiceNumber}`,
    billTabTitle: (invoiceNumber) => `Bill #${invoiceNumber}`,
    walkInCustomer: 'Walk-in Customer',
  },

  auditLog: {
    title: 'System Audit Log',
    subtitle:
      'Security trail, administrative modifications, and transactional ledger events',
    refreshTitle: 'Refresh Audit Logs',
    allActions: 'All Actions',
    emptyTable: 'No audit logs recorded.',
    inspectTitle: 'Inspect Audit Entry',
    detailsTitle: 'Audit Event Details',
    rawData: 'Raw Technical Data',
    json: 'JSON',
    copyJson: 'Copy JSON',
    copied: 'Copied',
    actors: {
      system: 'System',
      admin: 'Admin',
    },
    details: {
      timestamp: 'Timestamp',
      actor: 'Actor',
      actionEvent: 'Action / Event',
      moduleEntity: 'Module / Entity',
      entityIdentifier: 'Entity Identifier',
      eventDescription: 'Event Description',
    },
  },

  columns: {
    invoice: 'Invoice / Bill #',
    eventType: 'Event Type',
    soldQty: 'Sold Qty',
    soldUnitPrice: 'Sold Unit Price',
    soldTotalAmount: 'Sold Total Amount',
    payment: 'Payment',
    timestamp: 'Timestamp',
    actor: 'Actor',
    action: 'Action',
    module: 'Module',
    entityId: 'Entity ID',
  },

  filters: {
    fromLabel: 'From:',
    toLabel: 'To:',
    fromPlaceholder: 'From date',
    toPlaceholder: 'To date',
  },

  export: {
    selectProductWarning: 'Select a product with sales history to export',
    salesHistorySheet: 'Sales History',
    salesRecords: 'sales records',
    headers: {
      invoice: 'Invoice / Bill #',
      eventType: 'Event Type',
      soldQty: 'Sold Qty',
      soldUnitPrice: (symbol) => `Sold Unit Price (${symbol})`,
      soldTotalAmount: (symbol) => `Sold Total Amount (${symbol})`,
    },
  },
};

export default productFlow;
