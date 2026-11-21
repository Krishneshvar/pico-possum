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

const reportTypes = {
  today: 'Today',
  daily: 'Daily',
  weekly: 'Weekly',
  monthly: 'Monthly',
  yearly: 'Yearly',
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

  periodOptions: reportTypes,

  filters: {
    period: 'Period:',
    from: 'From:',
    to: 'To:',
    dateTo: 'to',
    fromDate: 'From date',
    toDate: 'To date',
    fromDatePlaceholder: 'From date',
    toDatePlaceholder: 'To date',
    advancedFilters: 'Advanced Filters',
    filterThreshold: 'Filter Threshold:',
    minAmountPlaceholder: (symbol) => `Min ${symbol}`,
    maxAmountPlaceholder: (symbol) => `Max ${symbol}`,
    rangeSeparator: '–',
    reportTypes,
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
      `sales-report-${reportType}-${dateRange}.${extension}`,
  },
};

export default salesReports;
