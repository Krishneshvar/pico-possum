/**
 * Strings for the reports area (sales analytics dashboard, multi-year chart).
 */

const reports = {
  view: {
    title: 'Sales Analytics',
    subtitle: 'Revenue summaries, growth comparisons, and categorical breakdown',
    resetFilters: 'Reset filters',
    refreshData: 'Refresh analytics data',
    reportingPeriod: 'Reporting Period:',
    fromDatePlaceholder: 'From date',
    toDatePlaceholder: 'To date',
    rangeSeparator: 'to',
    thisMonth: 'This Month',
    allTime: 'All Time',
    paymentMethod: 'Payment Method:',
    paymentOptions: {
      all: (count) => `All Methods (${count})`,
      cash: (count) => `Cash (${count})`,
      upiQr: (count) => `UPI / QR (${count})`,
      card: (count) => `Card (${count})`,
      giftCard: (count) => `Gift Card (${count})`,
    },
    filterBannerPrefix: 'Filtered by payment method:',
    filterBannerSuffix:
      '. Method distribution breakdown is scoped to this selection.',
    showAllMethods: 'Show All Methods',
  },

  kpis: {
    totalRevenue: 'Total Revenue',
    grossTransactionVolume: 'Gross transaction volume',
    methodVolume: (method) => `${method} volume`,
    totalInvoices: 'Total Invoices',
    customerTransactions: 'Customer transactions',
    averageBillValue: 'Average Bill Value',
    perCheckoutAverage: 'Per checkout average',
    totalDiscounts: 'Total Discounts',
    customerSavings: 'Customer savings awarded',
  },

  payments: {
    title: 'Revenue by Payment Method',
    subtitle: 'Distribution across payment collection channels',
    total: (amount) => `Total: ${amount}`,
    cash: 'Cash',
    upiQr: 'UPI / QR',
    card: 'Card',
    giftCard: 'Gift Card',
    barTooltip: (label, amount, percent) => `${label}: ${amount} (${percent}%)`,
    legendPercent: (percent) => `(${percent}%)`,
    percentOfRevenue: (percent) => `${percent}% of total revenue`,
  },

  tables: {
    topCategories: 'Top Selling Categories',
    topProducts: 'Top Selling Products',
    category: 'Category',
    product: 'Product',
    products: 'Products',
    sold: 'Sold',
    qtySold: 'Qty Sold',
    totalRevenue: 'Total Revenue',
    totalSold: 'Total Sold:',
    totalQty: 'Total Qty:',
    sku: (sku) => `SKU: ${sku}`,
    loadingCategories: 'Loading categories...',
    loadingProducts: 'Loading products...',
  },

  empty: {
    noCategories: 'No category sales in this period.',
    noProducts: 'No product sales in this period.',
  },

  multiYear: {
    title: 'Multi-Year Revenue Comparison',
    subtitle: 'Monthly revenue performance trends across comparison years',
    methodSuffix: (method) => `(${method})`,
    comparisonRange: 'Comparison Range:',
    yearsOption: (years) => `${years} Years`,
    updating: 'Updating comparison data…',
    revenue: 'Revenue:',
    transactions: 'Transactions:',
    months: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  },
};

export default reports;
