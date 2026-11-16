/** Strings for the settings area. */

const settings = {
  header: {
    title: 'System Settings',
    subtitle:
      'Terminal configuration, receipt layout, peripheral hardware, and database maintenance',
    navHeading: 'Configuration',
  },

  nav: {
    general: { label: 'General', description: 'Store identity and date formats' },
    storeTax: { label: 'Store & Tax', description: 'Currency, GSTIN, and stock rules' },
    receipt: { label: 'Receipt Designer', description: 'Thermal layout and print design' },
    printing: { label: 'Devices & Printing', description: 'Printer selection and testing' },
    backup: { label: 'Backup & Restore', description: 'Safety snapshots and data recovery' },
    migration: { label: 'Imports & Migration', description: 'CSV imports and Java POS conversion' },
    security: { label: 'Security & Access', description: 'Master administrator credentials' },
    appearance: { label: 'Appearance', description: 'Theme mode and visual display' },
  },

  general: {
    title: 'General Settings',
    subtitle: 'Configure store identity, contact details, and display formats',
    storeProfile: 'Store Profile',
    storeName: 'Store / Business Name',
    storeNamePlaceholder: 'e.g. Acme Retailers',
    phone: 'Phone Number',
    phonePlaceholder: 'e.g. +91 9876543210',
    address: 'Address / Location',
    addressPlaceholder: 'e.g. Shop #4, Commercial Complex',
    dateTimeFormats: 'Date & Time Formats',
    dateDisplayFormat: 'Date Display Format',
    dateFormats: {
      dayFirst: 'DD-MM-YYYY (e.g. 26-10-2026)',
      monthFirst: 'MM-DD-YYYY (e.g. 10-26-2026)',
      iso: 'YYYY-MM-DD (e.g. 2026-10-26)',
      monthNameFirst: 'Month Date, Year (e.g. Oct 26, 2026)',
      dayBeforeMonthName: 'Date Month, Year (e.g. 26 Oct, 2026)',
    },
    timeDisplayFormat: 'Time Display Format',
    timeFormats: {
      twelveHour: '12 hour format (e.g. 02:45 PM)',
      twentyFourHour: '24 hour format (e.g. 14:45)',
    },
    save: 'Save General Settings',
  },

  storeTax: {
    title: 'Store & Tax Settings',
    subtitle: 'Tax registration, currency configuration, and inventory behavior controls',
    currencyGstin: 'Currency & GSTIN',
    currencySymbol: 'Currency Symbol',
    currencySymbolPlaceholder: 'e.g. $, €, £',
    currencySymbolHint: 'Displayed across POS checkout and financial reports',
    gstin: 'GSTIN / Tax ID',
    gstinPlaceholder: 'e.g. 22AAAAA0000A1Z5',
    gstinHint: 'Printed on customer receipts and tax invoices',
    inventoryBehavior: 'Inventory Behavior',
    autoSkuTitle: 'Automatic Numerical SKU',
    autoSkuDescription:
      'New products automatically receive the next sequential numerical SKU. After importing legacy data, numbering continues from the highest existing SKU.',
    negativeStockTitle: 'Allow Negative Stock (Ignore Stock Limits)',
    negativeStockDescription:
      'Allows cashiers to bill items even if current stock is zero or negative. Leave unchecked to prevent sales when stock is depleted.',
    save: 'Save Store & Tax Settings',
  },

  receipt: {
    title: 'Receipt Designer',
    subtitle: 'Configure thermal paper width, rearrange receipt sections, and preview live',
    paperRollWidth: 'Paper Roll Width',
    paperWidths: {
      wide: '3 inch (80 mm)',
      narrow: '2 inch (58 mm)',
    },
    sectionsHeading: 'Receipt Sections',
    sectionsHint: 'Toggle section visibility and adjust position using up/down arrows',
    sectionNames: {
      storeHeader: 'Store Header',
      billMetadata: 'Bill Metadata',
      itemsTable: 'Items Table',
      totals: 'Totals & Payments',
      footer: 'Footer Message',
    },
    moveUp: 'Move up',
    moveDown: 'Move down',
    alignment: 'Alignment',
    alignmentOptions: {
      left: 'Left',
      center: 'Center',
      right: 'Right',
    },
    fontSize: 'Font Size',
    fontSizeOptions: {
      small: 'Compact',
      medium: 'Standard',
      large: 'Prominent',
    },
    printTaxColumn: 'Print tax rate column on receipt',
    footerText: 'Footer Message Text',
    footerPlaceholder: 'e.g. Thank you for your business!',
    save: 'Save Receipt Settings',
    previewTitle: (width) => `Thermal Preview (${width})`,
    sampleTransaction: 'Sample Transaction',
  },

  /**
   * Sample values used only for the live receipt preview inside Settings.
   * These are UI copy for the demo receipt, not persisted business data.
   */
  sampleData: {
    storeName: 'Pico POS Store',
    address: '123 Main St, Tech City',
    phone: '9876543210',
    gstin: '22AAAAA0000A1Z5',
    footer: 'Thank you for your visit!',
    invoiceNumber: 'S26CH0000001',
    customerName: 'Walk-in Customer',
    billerName: 'Cashier',
    paymentMethod: 'CASH',
    productCadbury: 'Cadbury Silk 150g',
    productGreetingCard: 'Greeting Card Premium',
  },

  printing: {
    title: 'Devices & Printing',
    subtitle: 'Detected hardware printers and receipt output routing',
    printersDetected: (count) => `${count} Printer(s) Detected`,
    noPrintersDetected: 'No Printers Detected',
    activeDefault: (name) => `Active Default: ${name}`,
    usingOsDefault: 'Using OS default print routing',
    hardwareReady: 'Hardware Ready',
    checkCable: 'Check USB / Cable',
    connectedPrinters: 'Connected Printers',
    scanPrinters: 'Scan Printers',
    defaultPrinter: 'Default Thermal Printer',
    osDefaultOption: '-- Use OS Default Printer --',
    printerOption: (name, isDefault) => `${name} ${isDefault ? '(System Default)' : ''}`,
    printerHint:
      'Receipts printed during POS checkout will be automatically routed to this printer',
    saveDefaultPrinter: 'Save Default Printer',
    sendTestPrint: 'Send Test Print',
    testPrintHint: 'Verifies ESC/POS driver connection and line feed',
    guidelinesTitle: 'Thermal Printer Guidelines',
    guidelines: [
      'ESC/POS thermal printers (TVS, Epson, Xprinter, Rongta) connect seamlessly over USB or Network.',
      'Ensure zero-margin paper settings are selected in the printer driver properties for smooth continuous cutting.',
      'If plugging in a new printer via USB, click "Scan Printers" above to register it immediately.',
    ],
  },

  backup: {
    title: 'Backup & Data Recovery',
    subtitle:
      'Automated snapshots, on-demand archive backups, and safe point-in-time restore',
    maintenance: 'Database Maintenance',
    maintenanceHint: 'Mandatory backups occur on daily launch and app close',
    createBackup: 'Create Backup Now',
    openFolder: 'Open Folder',
    openFolderTitle: 'Open local folder',
    storagePath: 'Backup Storage Path',
    loadingLocation: 'Loading location...',
    latestSnapshot: 'Latest Automated Snapshot',
    noSnapshots: 'No snapshots created yet',
    availableBackups: (count) => `Available Backups (${count})`,
    uploadExternal: 'Upload External .db File',
    emptyState: 'No backup files found. Click "Create Backup Now" to record a snapshot.',
    table: {
      filename: 'Filename',
      type: 'Type',
      date: 'Date',
      size: 'Size',
      action: 'Action',
    },
    restore: 'Restore',
    confirmFileRestore: {
      title: 'Restore Database',
      message: (filename) => `Are you sure you want to restore from "${filename}"?`,
      details: [
        'Supports both native PicoPOSSUM backups and Java app database files.',
        'An automatic safety pre-restore backup will be created first.',
      ],
      confirmLabel: 'Restore Database',
    },
    confirmBackupRestore: {
      title: 'Restore Database',
      message: (filename) => `Restore the database from "${filename}"?`,
      details: [
        'A safety pre-restore backup will be created automatically before current data is replaced.',
      ],
      confirmLabel: 'Restore',
    },
    readFileFailed: (error) => `Failed to read file: ${error}`,
  },

  migration: {
    title: 'Imports & Migration',
    subtitle:
      'Batch import product, category, customer, and historical invoice data via CSV or legacy databases',
    legacyDetectedTitle: 'Previous Java POS Database Detected',
    legacyFound: 'Found local installation:',
    importPrevious: 'Import Previous POS Data',
    confirmImport: {
      title: 'Import Previous POS Data',
      message:
        'Import all categories, products, customers, and sales history from your previous Java POS installation?',
      confirmLabel: 'Import Data',
    },
    migrationResult: (products, categories, customers, sales) =>
      `✓ Successfully imported ${products} products, ${categories} categories, ${customers} customers, and ${sales} sales invoices.`,
    csvHeading: 'Spreadsheet (CSV) Data Import',
    csvDescription:
      'Upload standard UTF-8 formatted CSV files to import master records into PicoPOSSUM',
    cards: {
      categories: {
        title: 'Categories Import',
        columns: 'Columns: name, description',
        importLabel: 'Import Categories',
        success: 'Categories imported successfully',
      },
      products: {
        title: 'Products Catalog Import',
        columns: 'Columns: sku, name, category, price, cost, stock',
        importLabel: 'Import Products',
        success: 'Products imported successfully',
      },
      customers: {
        title: 'Customer Directory Import',
        columns: 'Columns: name, phone, email, address',
        importLabel: 'Import Customers',
        success: 'Customers imported successfully',
      },
      bills: {
        title: 'Historical Bills Import',
        columns: 'Legacy bill rows, items, and payments',
        importLabel: 'Import Bills',
        success: 'Bills imported successfully',
      },
    },
  },

  security: {
    title: 'Security & Credentials',
    subtitle: 'Manage administrator terminal password and credential safeguards',
    updateTitle: 'Update Administrator Password',
    updateHint:
      'Used for unlocking the terminal, sensitive returns, and administrative operations',
    currentPassword: 'Current Password',
    currentPasswordPlaceholder: 'Enter current password',
    newPassword: 'New Password',
    newPasswordPlaceholder: 'Enter new password (min. 4 characters)',
    confirmPassword: 'Confirm New Password',
    confirmPasswordPlaceholder: 'Repeat new password',
    updatePassword: 'Update Password',
    fillAllFields: 'Please fill in all password fields.',
    passwordTooShort: 'New password must be at least 4 characters long.',
    passwordsDoNotMatch: 'New passwords do not match.',
    updated: 'Password updated successfully!',
    updateFailed: 'Failed to update password',
  },

  appearance: {
    title: 'Appearance & Display',
    subtitle:
      'Configure visual theme and interface density for retail lighting environments',
    colorTheme: 'Color Theme',
    colorThemeHint:
      'Choose between the default high-readability light operational theme or the deep navy dark theme.',
    lightTitle: 'Light Theme (Recommended)',
    lightDescription:
      'Optimal contrast for bright store lighting, cashier counters, and all display types.',
    darkTitle: 'Deep Navy Dark',
    darkDescription: 'Designed for evening shifts and dimmer low-light retail terminals.',
    palette: {
      canvas: 'App Canvas',
      surface: 'Primary Surface',
      brand: 'Brand Blue',
      content: 'Content',
      navyCanvas: 'Deep Navy Canvas',
      brandAction: 'Brand Action',
    },
    paletteSwatch: (hex, label) => `${hex} ${label}`,
  },

  toasts: {
    settingsSaved: 'Settings saved successfully',
    saveSettingsFailed: (error) => `Failed to save settings: ${error}`,
    receiptSaved: 'Receipt settings saved successfully',
    saveReceiptFailed: (error) => `Failed to save receipt settings: ${error}`,
    testPrintSent: (printer) => `Test print sent to ${printer}`,
    defaultPrinter: 'default printer',
    systemPrintDialog: 'Opened system print dialog for test receipt',
    backupCreated: (filename) => `Backup snapshot created: ${filename}`,
    createBackupFailed: (error) => `Failed to create backup: ${error}`,
    restoreFailed: (error) => `Restore failed: ${error}`,
    migrationComplete: 'Legacy database migrated successfully',
    migrationFailed: (error) => `Migration failed: ${error}`,
    backupFolderOpened: 'Opened backup folder',
    backupFolderFailed: (error) => `Could not open backup folder: ${error}`,
  },
};

export default settings;
