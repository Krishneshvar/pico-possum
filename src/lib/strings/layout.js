/**
 * Application shell strings: navigation, workspace tabs and branding.
 */

const layout = {
  brand: {
    name: 'PicoPOSSUM',
    logoAlt: 'PicoPOSSUM',
    tagline: 'Retail Terminal',
    initial: 'P',
  },

  nav: {
    menus: {
      inventory: 'Inventory',
      sales: 'Sales',
      reports: 'Reports',
      system: 'System',
    },
    items: {
      products: 'Products',
      categories: 'Categories',
      stockActivity: 'Stock Activity',
      pointOfSale: 'Point of Sale',
      billHistory: 'Bill History',
      customers: 'Customers',
      salesReports: 'Sales Reports',
      salesAnalytics: 'Sales Analytics',
      productFlow: 'Product Flow',
      auditLog: 'Audit Log',
      settings: 'Settings',
    },
    profile: {
      defaultUser: 'Store Operator',
      sessionLabel: 'Administrator Session',
      settings: 'Settings',
      appearance: 'Appearance',
      lockTerminal: 'Lock Terminal',
    },
    toast: {
      locked: 'Terminal locked. Sign in to continue.',
      lockFailedMessage: (error) => `Failed to lock terminal: ${error}`,
      lockFailedTitle: 'Lock failed',
    },
    confirm: {
      lockTitle: 'Lock Terminal',
      lockMessage: 'Lock the point of sale terminal and require password to re-enter?',
      lockConfirm: 'Lock Terminal',
      cancel: 'Cancel',
    },
  },

  sidebar: {
    nav: {
      pointOfSale: 'Point of Sale',
      billHistory: 'Bill History',
      products: 'Products',
      categories: 'Categories',
      stockActivity: 'Stock Activity',
      customers: 'Customers',
      analyticsReports: 'Analytics & Reports',
      settings: 'Settings',
    },
    badgePos: 'POS',
    shortcuts: {
      searchProducts: 'Search Products',
      completeCheckout: 'Complete Checkout',
      switchBills: 'Switch Bills',
    },
  },

  tabs: {
    noTabs:
      'No tabs open — select a workspace from the Sales or Inventory menus above',
    unsavedTitle: 'Unsaved Changes',
    unsavedMessage: (title) =>
      `"${title}" has unsaved changes. Are you sure you want to close it?`,
    discardAndClose: 'Discard & Close',
    keepTabOpen: 'Keep Tab Open',
    unsavedIndicator: 'Unsaved changes',
    closeTabTitle: 'Close tab (Ctrl+W)',
    closeTab: 'Close Tab',
    closeOthers: 'Close Others',
    closeAll: 'Close All',
  },

  workspace: {
    noTabsTitle: 'No workspace tabs open',
    noTabsDescription:
      'Open the Point of Sale terminal or select a workspace from the navigation bar above.',
    initializing: 'Initializing Pico POS...',
    tabLimitMessage:
      'Maximum limit of 10 open tabs reached. Please close a tab before opening another workspace.',
    tabLimitTitle: 'Tab Limit Reached',
  },
};

export default layout;
