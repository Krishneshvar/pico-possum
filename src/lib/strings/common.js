/**
 * Shared UI strings reused across the application.
 *
 * Keep generic actions, field labels, states and component defaults here so the
 * same wording is used everywhere. Feature-specific strings live in their own
 * section files and are aggregated by `src/lib/strings.js`.
 */

const common = {
  app: {
    name: 'PicoPOSSUM',
    tagline: 'Retail Terminal',
  },

  actions: {
    save: 'Save',
    saveChanges: 'Save Changes',
    cancel: 'Cancel',
    close: 'Close',
    delete: 'Delete',
    edit: 'Edit',
    add: 'Add',
    create: 'Create',
    update: 'Update',
    confirm: 'Confirm',
    restore: 'Restore',
    import: 'Import',
    export: 'Export',
    download: 'Download',
    upload: 'Upload',
    search: 'Search',
    clear: 'Clear',
    clearFilters: 'Clear filters',
    reset: 'Reset',
    remove: 'Remove',
    view: 'View',
    back: 'Back',
    next: 'Next',
    previous: 'Previous',
    apply: 'Apply',
    open: 'Open',
    openFolder: 'Open Folder',
    refresh: 'Refresh',
    tryAgain: 'Try again',
    done: 'Done',
    dismiss: 'Dismiss',
    viewDetails: 'View details',
    hideDetails: 'Hide details',
    selectAll: 'Select all',
    copy: 'Copy',
    print: 'Print',
  },

  labels: {
    actions: 'Actions',
    name: 'Name',
    description: 'Description',
    status: 'Status',
    type: 'Type',
    date: 'Date',
    dateTime: 'Date & Time',
    time: 'Time',
    size: 'Size',
    amount: 'Amount',
    quantity: 'Quantity',
    price: 'Price',
    cost: 'Cost',
    total: 'Total',
    subtotal: 'Subtotal',
    discount: 'Discount',
    tax: 'Tax',
    paid: 'Paid',
    balance: 'Balance',
    notes: 'Notes',
    email: 'Email',
    phone: 'Phone',
    address: 'Address',
    city: 'City',
    pinCode: 'Pin Code',
    category: 'Category',
    customer: 'Customer',
    product: 'Product',
    sku: 'SKU',
    invoiceNumber: 'Invoice #',
    paymentMethod: 'Payment Method',
    filename: 'Filename',
    details: 'Details',
    required: 'Required',
    optional: 'Optional',
    id: 'ID',
    method: 'Method',
    reference: 'Reference',
    stock: 'Stock',
    unit: 'Unit',
    records: 'records',
    row: 'row',
    rows: 'rows',
    item: 'item',
    items: 'items',
  },

  states: {
    loading: 'Loading...',
    processing: 'Processing...',
    saving: 'Saving...',
    pleaseWait: 'Please wait…',
    noRecords: 'No records found.',
    noData: 'No data available',
    none: 'None',
    notAvailable: 'N/A',
    all: 'All',
    unknown: 'Unknown',
    emptyValue: '—',
  },

  placeholders: {
    searchRecords: 'Search records…',
    selectOption: 'Select option…',
    selectDate: 'Select date',
    startDate: 'Start date',
    endDate: 'End date',
    enterName: 'Enter name',
    enterDescription: 'Enter description',
  },

  table: {
    searchRecords: 'Search records…',
    clearFilters: 'Clear filters',
    clearSelected: 'Clear selected',
    allFilter: (label) => `All ${label}`,
    filterCount: (label, count) => `${label} (${count})`,
    sortBy: (label) => `Sort by ${label}`,
    sortByColumn: 'Sort by this column',
    dragToResize: 'Drag to resize, double-click to reset',
    actions: 'Actions',
    page: 'Page',
    of: 'of',
    from: 'from',
    to: 'to',
    rangeOf: (total) => ` of ${total}`,
    minPlaceholder: (unit) => `Min${unit ? ` (${unit})` : ''}`,
    maxPlaceholder: (unit) => `Max${unit ? ` (${unit})` : ''}`,
    firstPage: 'First page',
    previousPage: 'Previous page',
    nextPage: 'Next page',
    lastPage: 'Last page',
    ellipsis: '…',
  },

  datePicker: {
    months: [
      'January',
      'February',
      'March',
      'April',
      'May',
      'June',
      'July',
      'August',
      'September',
      'October',
      'November',
      'December',
    ],
    weekdays: ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'],
    selected: (value) => `Selected: ${value}`,
    pickDate: 'Pick a date',
    clearDate: 'Clear date',
    previousMonth: 'Previous Month',
    nextMonth: 'Next Month',
    today: 'Today',
    clear: 'Clear',
    close: 'Close',
    dateRange: 'Date Range',
    from: 'From',
    to: 'To',
  },

  dropdown: {
    actions: 'Actions',
  },

  exportButton: {
    export: 'Export',
    tooltip: 'Export data as CSV or Excel',
    exportCsv: 'Export as CSV (.csv)',
    exportXlsx: 'Export as Excel (.xlsx)',
  },

  toast: {
    types: {
      success: 'Success',
      error: 'Error',
      warning: 'Warning',
      info: 'Information',
    },
    notice: 'Notice',
    notifications: 'Notifications',
    dismiss: 'Dismiss notification',
  },

  confirm: {
    title: 'Confirm Action',
    promptTitle: 'Are you sure?',
    confirm: 'Confirm',
    cancel: 'Cancel',
    pleaseWait: 'Please wait…',
  },

  exportUtils: {
    exportedCountTo: (count, entityName, target) =>
      `Exported ${count} ${entityName} to ${target}`,
    exportedTo: (target) => `Exported ${target}`,
    exportFailed: 'Export Failed',
    failedToExport: 'Failed to export file',
    excelWorkbook: 'Excel Workbook (.xlsx)',
    csvSpreadsheet: 'CSV Spreadsheet (.csv)',
  },

  importCsv: {
    triggerTooltip: (entity) => `Import ${entity} from CSV or Excel`,
    modalSubtitle: (singular) => `Batch import ${singular} records from CSV or Excel`,
    closeModal: 'Close modal',
    importFromCsv: 'Import from CSV (.csv)',
    importFromExcel: 'Import from Excel (.xlsx)',
    formatSupport: 'Format Support: ',
    strictSchemaTitle: 'Strict Schema Notice',
    strictSchemaBody:
      'Only the supported fields listed below will be taken into the application and stored in the database. Any other columns or extra data present in your file will not be taken in or stored anywhere in the app.',
    supportedFields: 'Supported Fields',
    headersCaseInsensitive: 'Headers are case-insensitive',
    required: 'Required',
    optional: 'Optional',
    aliases: (aliases) => `(or ${aliases})`,
    processingFile: (name) => `Processing ${name}...`,
    processingDetail:
      'Parsing worksheet data, validating schema headers, and saving records to database...',
    importFailed: 'Import Failed',
    tryAnotherFile: 'Try another file',
    successTitle: (imported, singular) =>
      `Successfully imported ${imported} ${singular}${imported === 1 ? '' : 's'}`,
    totalRows: (total) => `${total} total rows processed from `,
    skippedRows: (skipped) => `${skipped} row${skipped === 1 ? '' : 's'} skipped`,
    importAnotherFile: 'Import Another File',
    unsupportedFileType: 'Unsupported file type. Please upload a .csv, .xlsx, or .xls file.',
    noSheets: 'The uploaded Excel workbook contains no sheets.',
    emptySheet: (name) => `Sheet "${name}" is empty.`,
    importComplete: (count) => `Import complete: ${count} records imported.`,
    parseFailed: 'Failed to parse and import file',
    importFailedTitle: 'Import failed',
    done: 'Done',
    cancel: 'Cancel',
  },

  /**
   * CSV/XLSX import schemas. The schema text is UI copy, so it is centralised
   * here and built with the active currency symbol.
   */
  importSchemas: (sym) => ({
    products: {
      title: 'Import Products',
      singular: 'product',
      required: [
        { name: 'Product Name', aliases: 'Name', desc: 'Product title / description' },
        { name: 'Product Code / SKU', aliases: 'SKU, Product Code', desc: 'Unique barcode or SKU identifier' },
        { name: 'MRP', aliases: 'Price, MRP/Price', desc: `Maximum Retail Price / MRP (${sym})` },
      ],
      optional: [
        { name: 'Category Name', aliases: 'Category, Division Name', desc: 'Category name (auto-created if new)' },
        { name: 'Cost Price', aliases: 'Avg Item Cost, Cost', desc: `Wholesale purchase / cost price (${sym})` },
        { name: 'Current Stock', aliases: 'Opening Stock, Stock', desc: 'Initial inventory quantity' },
        { name: 'Tax Rate', aliases: 'Tax%', desc: 'GST percentage rate (e.g. 5, 12, 18)' },
        { name: 'Stock Alert', aliases: 'Minimum Stock Level', desc: 'Low stock warning threshold' },
        { name: 'Division Code', aliases: 'Category Code', desc: 'Numeric division code to map category' },
      ],
    },
    categories: {
      title: 'Import Categories',
      singular: 'category',
      required: [
        { name: 'Category Name', aliases: 'Division Name, Name', desc: 'Unique category or division name' },
      ],
      optional: [
        { name: 'Division Code', aliases: 'Category Code, Code', desc: 'Numeric division identifier' },
      ],
    },
    customers: {
      title: 'Import Customers',
      singular: 'customer',
      required: [
        { name: 'Customer Name', aliases: 'Name', desc: 'Full name of customer or account' },
      ],
      optional: [
        { name: 'Contact Number', aliases: 'Phone, Mobile', desc: 'Primary contact or mobile number' },
        { name: 'Customer Address', aliases: 'Address', desc: 'Billing or street address' },
        { name: 'City', aliases: 'City', desc: 'City or district' },
        { name: 'Pin Code', aliases: 'Postal Code, Pincode', desc: 'Postal PIN code' },
        { name: 'Customer Code', aliases: 'Code, ID', desc: 'Customer account identifier' },
      ],
    },
    bills: {
      title: 'Import Bill History',
      singular: 'bill',
      compatibility:
        "Supports both this application's own export files (CSV & Excel) and legacy billing data from external POS systems.",
      required: [
        {
          name: 'Invoice # / Bill Number',
          aliases: 'Invoice #, Invoice Number, Bill Number, Bill No, Invoice No',
          desc: 'Unique bill or invoice number (matches this app\'s export "Invoice #" and legacy formats)',
        },
        {
          name: 'Date / Bill Date',
          aliases: 'Date, Bill Date, Sale Date',
          desc: 'Sale date (DD/MM/YYYY, YYYY-MM-DD, or Month Date, Year)',
        },
        {
          name: `Net Total (${sym}) / Net Amount`,
          aliases: `Net Total (${sym}), Net Total, Net Amount, Total Amount, Total, Amount`,
          desc: `Final bill total in ${sym} (matches this app's export "Net Total (${sym})")`,
        },
      ],
      optional: [
        {
          name: 'Time / Bill Time',
          aliases: 'Time, Bill Time',
          desc: 'Sale time (12-hour AM/PM or 24-hour HH:MM, defaults to 00:00:00)',
        },
        {
          name: 'Customer Name',
          aliases: 'Customer Name, Customer, Client',
          desc: 'Customer name (defaults to Walk-in Customer)',
        },
        {
          name: 'Customer Phone',
          aliases: 'Customer Phone, Phone, Mobile, Contact Number',
          desc: 'Customer contact phone (auto-linked to customer directory if found)',
        },
        {
          name: 'Payment Method',
          aliases: 'Payment Method, Payment Mode, Mode',
          desc: 'Payment mode (e.g. Cash, UPI, Card; inferred from bill # if omitted)',
        },
        {
          name: `Subtotal (${sym})`,
          aliases: `Subtotal (${sym}), Subtotal, Sub Total`,
          desc: 'Pre-tax gross subtotal (defaults to Net Total if omitted)',
        },
        {
          name: `Discount (${sym})`,
          aliases: `Discount (${sym}), Discount, Disc`,
          desc: `Total discount amount in ${sym} (defaults to 0.00)`,
        },
        {
          name: `Tax (${sym})`,
          aliases: `Tax (${sym}), Tax, GST, VAT`,
          desc: `Total tax amount in ${sym} (defaults to 0.00)`,
        },
        {
          name: 'Status',
          aliases: 'Status, Bill Status',
          desc: 'Sale status (PAID, PARTIALLY_PAID, CANCELLED, REFUNDED; defaults to PAID)',
        },
        {
          name: 'Notes',
          aliases: 'Notes, Note, Remarks',
          desc: 'Optional bill memo or remarks',
        },
      ],
    },
  }),
};

export default common;
