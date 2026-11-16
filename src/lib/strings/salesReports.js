/**
 * Strings for the sales reports area: period/date filters, report columns,
 * totals bar, empty states and export copy.
 */

const columns = {
  periodLabel: (reportType) =>
    reportType === 'monthly' ? 'Month' : reportType === 'yearly' ? 'Year' : 'Date',
  billsCount: 'Bills Count',
  cashSettled: 'Cash Settled',
  upiQr: 'UPI / QR',
  cardTerminal: 'Card Terminal',
  giftCard: 'Gift Card',
  discounts: 'Discounts',
  grossSales: 'Gross Sales',
  netSales: 'Net Sales',
};

const salesReports = {
  view: {
    title: 'Financial Sales Reports',
    subtitle: 'Periodic reconciliation and payment collection audits',
    refreshReport: 'Refresh Report',
    columnPicker: (count) => `Columns (${count})`,
    visibleColumns: 'Visible Columns',
  },

  columns,

  filters: {
    period: 'Period:',
    from: 'From:',
    to: 'To:',
    fromDatePlaceholder: 'From date',
    toDatePlaceholder: 'To date',
    advancedFilters: 'Advanced Filters',
    filterThreshold: 'Filter Threshold:',
    minAmountPlaceholder: (symbol) => `Min ${symbol}`,
    maxAmountPlaceholder: (symbol) => `Max ${symbol}`,
    rangeSeparator: '–',
    reportTypes: {
      today: 'Today',
      daily: 'Daily',
      monthly: 'Monthly',
      yearly: 'Yearly',
    },
    amountFields: {
      gross: columns.grossSales,
      net: columns.netSales,
      cash: 'Cash',
      upi: 'UPI',
      card: 'Card',
      giftCard: columns.giftCard,
    },
  },

  summary: {
    totals: 'Totals',
    transactions: (count) => `${count} txns`,
  },

  empty: {
    noRecords: 'No sales records found for the selected period.',
  },

  export: {
    noData: 'No report data to export',
    entityName: 'report rows',
    sheetName: 'Sales Report',
    filename: (reportType, dateRange, extension) =>
      `sales_report_${reportType}_${dateRange}.${extension}`,
  },
};

export default salesReports;
