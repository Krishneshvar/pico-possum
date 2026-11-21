/** Strings for the customers area. */

const customers = {
  view: {
    title: 'Customers Directory',
    subtitle:
      'Manage accounts, loyalty history, contact details, and past purchases',
    empty: 'No customers found in directory.',
  },

  actions: {
    addCustomer: 'Add Customer',
    purchaseHistory: 'Purchase History',
    editCustomer: 'Edit Customer',
    deleteCustomer: 'Delete Customer',
  },

  columns: {
    name: 'Customer Name',
    phone: 'Phone Number',
    billsCount: 'Bills Count',
    totalSpent: 'Total Spent',
    lastVisit: 'Last Visit',
  },

  profile: {
    fallbackInitial: 'C',
    phone: (phone) => `Ph: ${phone}`,
    stats: {
      totalOrders: 'Total Orders',
      totalSpent: 'Total Spent',
      averageOrder: 'Average Order',
      lastOrder: 'Last Order',
    },
    history: {
      empty: 'No purchase history found for this customer.',
      defaultPaymentMethod: 'CASH',
      columns: {
        invoice: 'Invoice #',
        date: 'Date',
        payment: 'Payment',
        status: 'Status',
        amount: 'Amount',
        action: 'Action',
      },
    },
  },

  form: {
    addTitle: 'New Customer',
    editTitle: 'Edit Customer',
    name: 'Customer Name',
    phone: 'Phone Number',
    address: 'Address / Notes',
    namePlaceholder: 'e.g. John Doe',
    phonePlaceholder: 'e.g. 9876543210',
    addressPlaceholder: 'Street / locality',
    saveCustomer: 'Save Customer',
    updateCustomer: 'Update Customer',
  },

  dialogs: {
    deleteTitle: (name) => `Delete Customer "${name}"?`,
    deleteMessage: (name) =>
      `Are you sure you want to delete customer "${name}"? Past transactions referencing this customer will retain their record details.`,
    deleteConfirm: 'Delete Customer',
  },

  toasts: {
    created: (name) => `Customer "${name}" added successfully`,
    createFailed: (error) => `Failed to add customer: ${error}`,
    createFailedTitle: 'Create failed',
    updated: (name) => `Customer "${name}" updated successfully`,
    updateFailed: (error) => `Failed to update customer: ${error}`,
    updateFailedTitle: 'Update failed',
    deleted: 'Customer deleted successfully',
    deleteFailed: (error) => `Failed to delete customer: ${error}`,
    deleteFailedTitle: 'Delete failed',
    nameRequired: 'Customer name is required',
    nothingToExport: 'No customers to export',
  },

  export: {
    entityName: 'customers',
    sheetName: 'Customers',
    headers: {
      customerName: 'Customer Name',
      phone: 'Phone Number',
      address: 'Address',
      totalBills: 'Total Bills',
      totalSpent: (currencySymbol) => `Total Spent (${currencySymbol})`,
      lastVisit: 'Last Visit',
    },
  },

  billTabTitle: (invoiceNumber) => `Bill #${invoiceNumber}`,
};

export default customers;
