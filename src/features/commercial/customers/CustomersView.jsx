import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Users, Plus, Phone, MapPin, X, Edit2, Trash2, Receipt, ArrowRight, ShoppingBag, Clock } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import { formatDateTime, formatDate, formatPaise } from '../../../lib/formatters';
import * as XLSX from 'xlsx';
import DataTable from '../../../components/common/DataTable';
import SortableTh from '../../../components/common/SortableTh';
import DropdownActions from '../../../components/common/DropdownActions';
import ImportCsvButton from '../../../components/common/ImportCsvButton';
import ExportButton from '../../../components/common/ExportButton';
import { exportWithUserLocation, escapeCsv } from '../../../lib/exportUtils';
import { nextSortState, sortRows } from '../../../lib/sort';
import { useWorkspaceStore } from '../../../stores/workspaceStore';

export default function CustomersView() {
  const queryClient = useQueryClient();
  const { openOrFocus } = useWorkspaceStore();

  const [modalOpen, setModalOpen] = useState(false);
  const [editingCustomer, setEditingCustomer] = useState(null);
  const [formData, setFormData] = useState({ name: '', phone: '', address: '' });
  const [viewingCustomer, setViewingCustomer] = useState(null);
  const [historySort, setHistorySort] = useState({ key: null, dir: null });

  const { data: customers = [], isLoading } = useQuery({
    queryKey: ['customers'],
    queryFn: () => tauriApi.getCustomers(null),
  });

  const { data: customerSales = [], isLoading: loadingCustomerSales } = useQuery({
    queryKey: ['customer_sales', viewingCustomer?.id],
    queryFn: () => tauriApi.getCustomerSales(viewingCustomer.id),
    enabled: !!viewingCustomer?.id,
  });

  const sortedCustomerSales = useMemo(
    () => sortRows(customerSales, historySort, (row, key) => row[key]),
    [customerSales, historySort]
  );

  const customerStats = useMemo(() => {
    if (!customerSales || customerSales.length === 0) {
      return { totalOrders: 0, totalSpent: 0, avgBill: 0, lastOrder: null };
    }
    const validSales = customerSales.filter((s) => s.status !== 'CANCELLED');
    const totalSpent = validSales.reduce((acc, s) => acc + (s.total_paise || 0), 0);
    const totalOrders = validSales.length;
    const avgBill = validSales.length > 0 ? Math.round(totalSpent / validSales.length) : 0;
    const lastOrder = validSales[0]?.created_at || customerSales[0]?.created_at || null;
    return { totalOrders, totalSpent, avgBill, lastOrder };
  }, [customerSales]);

  const createMutation = useMutation({
    mutationFn: (payload) => tauriApi.createCustomer(payload),
    onSuccess: (_data, payload) => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      setModalOpen(false);
      setFormData({ name: '', phone: '', address: '' });
      toast.success(`Customer "${payload.name}" added successfully`);
    },
    onError: (err) => toast.error(`Failed to add customer: ${err}`, { title: 'Create failed' }),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => tauriApi.updateCustomer(id, payload),
    onSuccess: (_data, { payload }) => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      setModalOpen(false);
      setEditingCustomer(null);
      setFormData({ name: '', phone: '', address: '' });
      toast.success(`Customer "${payload.name}" updated successfully`);
    },
    onError: (err) => toast.error(`Failed to update customer: ${err}`, { title: 'Update failed' }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id) => tauriApi.deleteCustomer(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      toast.success('Customer deleted successfully');
    },
    onError: (err) => toast.error(`Failed to delete customer: ${err}`, { title: 'Delete failed' }),
  });

  const handleOpenCreate = () => {
    setEditingCustomer(null);
    setFormData({ name: '', phone: '', address: '' });
    setModalOpen(true);
  };

  const handleOpenEdit = (customer) => {
    setEditingCustomer(customer);
    setFormData({
      name: customer.name || '',
      phone: customer.phone || '',
      address: customer.address || '',
    });
    setModalOpen(true);
  };

  const handleDelete = async (customer) => {
    const { confirmed } = await confirmDialog({
      title: `Delete Customer "${customer.name}"?`,
      message: `Are you sure you want to delete customer "${customer.name}"? Past transactions referencing this customer will retain their record details.`,
      confirmLabel: 'Delete Customer',
      cancelLabel: 'Cancel',
      tone: 'danger',
    });
    if (confirmed) {
      deleteMutation.mutate(customer.id);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.name.trim()) {
      toast.warning('Customer name is required');
      return;
    }
    const payload = {
      name: formData.name.trim(),
      phone: formData.phone.trim() || null,
      address: formData.address.trim() || null,
    };
    if (editingCustomer) {
      updateMutation.mutate({ id: editingCustomer.id, payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  const handleOpenBillDetail = (sale) => {
    openOrFocus(
      `Bill #${sale.invoice_number}`,
      'bill-detail',
      { saleId: sale.id, invoiceNumber: sale.invoice_number },
      `bill-${sale.id}`
    );
  };

  const handleExportCsv = async () => {
    if (!customers.length) {
      toast.warning('No customers to export');
      return;
    }

    const headers = [
      'Customer Name',
      'Phone Number',
      'Address',
      'Total Bills',
      'Total Spent (Rs)',
      'Last Visit',
    ];

    const rows = customers.map((c) => [
      escapeCsv(c.name || ''),
      escapeCsv(c.phone || ''),
      escapeCsv(c.address || ''),
      escapeCsv(c.total_sales || 0),
      escapeCsv(((c.total_spent_paise || 0) / 100).toFixed(2)),
      escapeCsv(c.last_sale_date ? formatDate(c.last_sale_date) : ''),
    ]);

    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\r\n');
    const dateStr = new Date().toISOString().split('T')[0];
    await exportWithUserLocation({
      filename: `customers_${dateStr}.csv`,
      extension: 'csv',
      data: csvContent,
      count: customers.length,
      entityName: 'customers',
    });
  };

  const handleExportXlsx = async () => {
    if (!customers.length) {
      toast.warning('No customers to export');
      return;
    }

    const headers = [
      'Customer Name',
      'Phone Number',
      'Address',
      'Total Bills',
      'Total Spent (Rs)',
      'Last Visit',
    ];

    const rows = customers.map((c) => [
      c.name || '',
      c.phone || '',
      c.address || '',
      c.total_sales || 0,
      parseFloat(((c.total_spent_paise || 0) / 100).toFixed(2)),
      c.last_sale_date ? formatDate(c.last_sale_date) : '—',
    ]);

    const ws = XLSX.utils.aoa_to_sheet([headers, ...rows]);
    ws['!cols'] = [
      { wch: 25 },
      { wch: 18 },
      { wch: 30 },
      { wch: 14 },
      { wch: 18 },
      { wch: 14 },
    ];
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Customers');
    const xlsxArrayBuffer = XLSX.write(wb, { type: 'array', bookType: 'xlsx' });
    const dateStr = new Date().toISOString().split('T')[0];
    await exportWithUserLocation({
      filename: `customers_${dateStr}.xlsx`,
      extension: 'xlsx',
      data: xlsxArrayBuffer,
      count: customers.length,
      entityName: 'customers',
    });
  };

  const columns = [
    {
      key: 'name',
      label: 'Customer Name',
      className: 'font-semibold text-text-primary text-xs',
      render: (r) => (
        <span
          onClick={(e) => {
            e.stopPropagation();
            setViewingCustomer(r);
          }}
          className="hover:underline cursor-pointer text-brand"
        >
          {r.name}
        </span>
      ),
    },
    {
      key: 'phone',
      label: 'Phone Number',
      className: 'font-mono text-text-muted text-xs',
      render: (r) => r.phone || '—',
    },
    {
      key: 'total_bills',
      label: 'Bills Count',
      headerClassName: 'text-center',
      className: 'text-center text-xs font-mono',
      render: (r) => (
        <span className="px-2 py-0.5 rounded-full bg-surface-secondary border border-border-default text-text-secondary text-[11px]">
          {r.total_sales || 0}
        </span>
      ),
    },
    {
      key: 'total_spent_paise',
      label: 'Total Spent',
      headerClassName: 'text-right',
      className: 'text-right financial-value font-bold text-text-primary text-xs',
      render: (r) => formatPaise(r.total_spent_paise || 0),
    },
    {
      key: 'last_visit',
      label: 'Last Visit',
      className: 'text-text-muted text-xs whitespace-nowrap',
      render: (r) => (r.last_sale_date ? formatDate(r.last_sale_date) : '—'),
    },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas overflow-hidden">
      {/* Header */}
      <div className="h-14 px-5 border-b border-border-default bg-surface flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-brand-subtle text-brand flex items-center justify-center">
            <Users className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-text-primary">Customers Directory</h2>
            <p className="text-[11px] text-text-muted">
              Manage accounts, loyalty history, contact details, and past purchases
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <ImportCsvButton
            entity="customers"
            onComplete={() => {
              queryClient.invalidateQueries({ queryKey: ['customers'] });
            }}
          />

          <ExportButton
            onExportCsv={handleExportCsv}
            onExportXlsx={handleExportXlsx}
          />

          <button
            type="button"
            onClick={handleOpenCreate}
            className="px-3.5 py-1.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-bold text-xs flex items-center gap-1.5 shadow-xs transition-colors cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add Customer</span>
          </button>
        </div>
      </div>

      {/* Directory Table */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          persistKey="customers"
          data={customers}
          keyFn={(r) => r.id}
          searchKeys={['name', 'phone', 'address']}
          isLoading={isLoading}
          emptyMessage="No customers found in directory."
          onRowClick={(row) => setViewingCustomer(row)}
          actions={(row) => (
            <DropdownActions
              items={[
                {
                  label: 'Purchase History',
                  icon: <Receipt className="w-3.5 h-3.5" />,
                  onClick: () => setViewingCustomer(row),
                },
                {
                  label: 'Edit Customer',
                  icon: <Edit2 className="w-3.5 h-3.5" />,
                  onClick: () => handleOpenEdit(row),
                },
                'divider',
                {
                  label: 'Delete Customer',
                  icon: <Trash2 className="w-3.5 h-3.5" />,
                  danger: true,
                  onClick: () => handleDelete(row),
                },
              ]}
            />
          )}
        />
      </div>

      {/* ── Customer Profile & Purchase History Modal ── */}
      {viewingCustomer && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-surface border border-border-default rounded-2xl max-w-2xl w-full p-6 space-y-4 shadow-xl max-h-[90vh] flex flex-col text-text-primary animate-scale-in">
            <div className="flex justify-between items-center border-b border-border-default pb-3 shrink-0">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-brand-subtle text-brand flex items-center justify-center font-bold text-base">
                  {(viewingCustomer.name || 'C')[0].toUpperCase()}
                </div>
                <div>
                  <h3 className="font-bold text-base text-text-primary">{viewingCustomer.name}</h3>
                  <div className="flex items-center gap-3 text-xs text-text-muted mt-0.5">
                    {viewingCustomer.phone && (
                      <span className="font-mono">Ph: {viewingCustomer.phone}</span>
                    )}
                    {viewingCustomer.address && <span>• {viewingCustomer.address}</span>}
                  </div>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setViewingCustomer(null)}
                className="p-1 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-secondary cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Compact Lifetime Stats Grid */}
            <div className="grid grid-cols-4 gap-2.5 text-xs shrink-0">
              <div className="p-3 bg-surface-secondary rounded-xl border border-border-default text-center">
                <span className="text-text-muted block text-[11px]">Total Orders</span>
                <span className="text-base font-bold text-text-primary font-mono mt-0.5 block">
                  {customerStats.totalOrders}
                </span>
              </div>
              <div className="p-3 bg-surface-secondary rounded-xl border border-border-default text-center">
                <span className="text-text-muted block text-[11px]">Total Spent</span>
                <span className="text-base font-bold text-text-primary financial-value mt-0.5 block">
                  {formatPaise(customerStats.totalSpent)}
                </span>
              </div>
              <div className="p-3 bg-surface-secondary rounded-xl border border-border-default text-center">
                <span className="text-text-muted block text-[11px]">Average Order</span>
                <span className="text-base font-bold text-text-primary financial-value mt-0.5 block">
                  {formatPaise(customerStats.avgBill)}
                </span>
              </div>
              <div className="p-3 bg-surface-secondary rounded-xl border border-border-default text-center">
                <span className="text-text-muted block text-[11px]">Last Order</span>
                <span className="text-xs font-bold text-text-secondary mt-1 block truncate">
                  {customerStats.lastOrder ? formatDate(customerStats.lastOrder) : 'None'}
                </span>
              </div>
            </div>

            {/* Invoices List */}
            <div className="flex-1 overflow-y-auto border border-border-default rounded-xl bg-surface shadow-2xs">
              <table className="w-full text-left border-collapse text-xs select-text">
                <thead className="bg-table-header-bg text-[11px] font-semibold text-table-header-text uppercase tracking-wider sticky top-0 border-b border-table-header-border z-10 shadow-2xs">
                  <tr>
                    <SortableTh
                      sortKey="invoice_number"
                      sort={historySort}
                      onSort={(key) => setHistorySort((p) => nextSortState(p, key))}
                      className="py-2.5 px-3.5 pl-4"
                    >
                      Invoice #
                    </SortableTh>
                    <SortableTh
                      sortKey="created_at"
                      sort={historySort}
                      onSort={(key) => setHistorySort((p) => nextSortState(p, key))}
                      className="py-2.5 px-3.5"
                    >
                      Date
                    </SortableTh>
                    <SortableTh
                      sortKey="payment_method"
                      sort={historySort}
                      onSort={(key) => setHistorySort((p) => nextSortState(p, key))}
                      className="py-2.5 px-3.5"
                    >
                      Payment
                    </SortableTh>
                    <SortableTh
                      sortKey="status"
                      sort={historySort}
                      onSort={(key) => setHistorySort((p) => nextSortState(p, key))}
                      align="center"
                      className="py-2.5 px-3.5"
                    >
                      Status
                    </SortableTh>
                    <SortableTh
                      sortKey="total_paise"
                      sort={historySort}
                      onSort={(key) => setHistorySort((p) => nextSortState(p, key))}
                      align="right"
                      className="py-2.5 px-3.5"
                    >
                      Amount
                    </SortableTh>
                    <th className="py-2.5 pl-3.5 pr-4 text-right w-20">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-default">
                  {loadingCustomerSales ? (
                    <tr>
                      <td colSpan="6" className="py-10 text-center text-text-muted">
                        <div className="inline-block w-4 h-4 border-2 border-brand border-t-transparent rounded-full animate-spin" />
                      </td>
                    </tr>
                  ) : sortedCustomerSales.length === 0 ? (
                    <tr>
                      <td colSpan="6" className="py-10 text-center text-text-muted font-sans">
                        No purchase history found for this customer.
                      </td>
                    </tr>
                  ) : (
                    sortedCustomerSales.map((sale) => (
                      <tr key={sale.id} className="hover:bg-surface-secondary/70 transition-colors">
                        <td className="py-2 px-3.5 pl-4 font-bold font-mono text-text-primary">
                          {sale.invoice_number}
                        </td>
                        <td className="py-2 px-3.5 text-text-muted text-[11px]">
                          {formatDateTime(sale.created_at)}
                        </td>
                        <td className="py-2 px-3.5">
                          <span className="px-1.5 py-0.5 rounded bg-surface-secondary text-text-secondary text-[10px] uppercase border border-border-default">
                            {sale.payment_method ? sale.payment_method.replace(/_/g, ' ') : 'CASH'}
                          </span>
                        </td>
                        <td className="py-2 px-3.5 text-center">
                          <span
                            className={`inline-flex px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase ${
                              sale.status === 'CANCELLED'
                                ? 'bg-danger-bg text-danger'
                                : 'bg-success-bg text-success'
                            }`}
                          >
                            {sale.status}
                          </span>
                        </td>
                        <td className="py-2 px-3.5 text-right font-bold financial-value text-text-primary">
                          {formatPaise(sale.total_paise)}
                        </td>
                        <td className="py-2 pl-3.5 pr-4 text-right">
                          <button
                            type="button"
                            onClick={() => {
                              handleOpenBillDetail(sale);
                              setViewingCustomer(null);
                            }}
                            className="px-2 py-1 rounded bg-surface hover:bg-surface-secondary text-text-primary border border-border-default text-[11px] inline-flex items-center gap-1 transition-colors cursor-pointer"
                          >
                            <span>Open</span>
                            <ArrowRight className="w-3 h-3 text-brand" />
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            <div className="flex justify-end pt-2 border-t border-border-default shrink-0">
              <button
                type="button"
                onClick={() => setViewingCustomer(null)}
                className="px-4 py-1.5 rounded-lg text-xs font-semibold text-text-secondary hover:bg-surface-secondary border border-border-default cursor-pointer"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add / Edit Customer Modal */}
      {modalOpen && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-surface border border-border-default rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-xl text-text-primary animate-scale-in">
            <div className="flex justify-between items-center border-b border-border-default pb-3">
              <h3 className="font-bold text-sm text-text-primary">
                {editingCustomer ? 'Edit Customer' : 'New Customer'}
              </h3>
              <button
                type="button"
                onClick={() => setModalOpen(false)}
                className="p-1 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-secondary cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-3 text-xs">
              <div>
                <label className="text-text-secondary block font-semibold mb-1">
                  Customer Name <span className="text-danger">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="e.g. Anand R."
                  className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none"
                />
              </div>
              <div>
                <label className="text-text-secondary block font-semibold mb-1">Phone Number</label>
                <input
                  type="text"
                  value={formData.phone}
                  onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                  placeholder="e.g. 9876543210"
                  className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none font-mono"
                />
              </div>
              <div>
                <label className="text-text-secondary block font-semibold mb-1">Address / Notes</label>
                <textarea
                  rows="2"
                  value={formData.address}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  placeholder="Street / locality"
                  className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none resize-none"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2 border-t border-border-default">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="px-3 py-1.5 rounded-lg font-semibold text-text-secondary hover:bg-surface-secondary border border-border-default cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  className="px-4 py-1.5 rounded-lg font-bold bg-brand text-white hover:bg-brand-hover shadow-xs disabled:opacity-50 cursor-pointer"
                >
                  {editingCustomer ? 'Update Customer' : 'Save Customer'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
