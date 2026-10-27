import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Users, Plus, Phone, MapPin, X, Edit2, Trash2, Receipt, ArrowRight, ShoppingBag, Clock } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatDateTime, formatDate, formatPaise } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';
import DropdownActions from '../../../components/common/DropdownActions';
import ImportCsvButton from '../../../components/common/ImportCsvButton';
import { useWorkspaceStore } from '../../../stores/workspaceStore';


export default function CustomersView() {
  const queryClient = useQueryClient();
  const { openOrFocus } = useWorkspaceStore();

  const [modalOpen, setModalOpen] = useState(false);
  const [editingCustomer, setEditingCustomer] = useState(null);
  const [formData, setFormData] = useState({ name: '', phone: '', address: '' });
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [viewingCustomer, setViewingCustomer] = useState(null);

  const { data: customers = [], isLoading } = useQuery({
    queryKey: ['customers'],
    queryFn: () => tauriApi.getCustomers(null),
  });

  const { data: customerSales = [], isLoading: loadingCustomerSales } = useQuery({
    queryKey: ['customer_sales', viewingCustomer?.id],
    queryFn: () => tauriApi.getCustomerSales(viewingCustomer.id),
    enabled: !!viewingCustomer?.id,
  });

  const customerStats = useMemo(() => {
    if (!customerSales || customerSales.length === 0) {
      return { totalOrders: 0, totalSpent: 0, avgBill: 0, lastOrder: null };
    }
    const validSales = customerSales.filter((s) => s.status !== 'CANCELLED');
    const totalSpent = validSales.reduce((acc, s) => acc + (s.total_paise || 0), 0);
    const totalOrders = customerSales.length;
    const avgBill = validSales.length > 0 ? Math.round(totalSpent / validSales.length) : 0;
    const lastOrder = customerSales[0]?.created_at || null;
    return { totalOrders, totalSpent, avgBill, lastOrder };
  }, [customerSales]);

  const createMutation = useMutation({
    mutationFn: (payload) => tauriApi.createCustomer(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      setModalOpen(false);
      setFormData({ name: '', phone: '', address: '' });
    },
    onError: (err) => alert(`Error: ${err}`),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => tauriApi.updateCustomer(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      setModalOpen(false);
      setEditingCustomer(null);
      setFormData({ name: '', phone: '', address: '' });
    },
    onError: (err) => alert(`Error: ${err}`),
  });

  const deleteMutation = useMutation({
    mutationFn: (id) => tauriApi.deleteCustomer(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      setDeleteTarget(null);
    },
    onError: (err) => alert(`Error deleting customer: ${err}`),
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

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.name.trim()) return;
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

  const columns = [
    {
      key: 'name', label: 'Name',
      className: 'font-semibold text-slate-200',
    },
    {
      key: 'phone', label: 'Phone',
      className: 'font-mono text-slate-400',
      render: (r) => r.phone
        ? <span className="flex items-center gap-1.5"><Phone className="w-3 h-3 text-slate-500" />{r.phone}</span>
        : <span className="text-slate-600">—</span>,
    },
    {
      key: 'address', label: 'Address',
      className: 'text-slate-400',
      render: (r) => r.address
        ? <span className="flex items-center gap-1.5"><MapPin className="w-3 h-3 text-slate-500 shrink-0" /><span className="truncate max-w-xs">{r.address}</span></span>
        : <span className="text-slate-600">—</span>,
    },
    {
      key: 'created_at', label: 'Joined',
      className: 'text-slate-500 text-[11px] font-mono',
      render: (r) => formatDateTime(r.created_at),
    },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-14 px-5 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <Users className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-100">Customer Directory</h2>
            <p className="text-[11px] text-slate-500">Manage customer contacts, profiles, and order history</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <ImportCsvButton
            entity="customers"
            onComplete={() => {
              queryClient.invalidateQueries({ queryKey: ['customers'] });
            }}
          />
          <button
            onClick={handleOpenCreate}
            className="px-3.5 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-1.5 shadow-lg shadow-emerald-500/20 transition-all cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />
            Add Customer
          </button>
        </div>
      </div>


      {/* Data Table */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          persistKey="customers"
          data={customers}
          keyFn={(r) => r.id}
          searchKeys={['name', 'phone', 'address']}
          isLoading={isLoading}
          emptyMessage="No customers found."
          onRowClick={(row) => setViewingCustomer(row)}
          actions={(row) => (
            <DropdownActions items={[
              { label: 'Purchase History', icon: <Receipt className="w-3.5 h-3.5" />, onClick: () => setViewingCustomer(row) },
              { label: 'Edit Customer', icon: <Edit2 className="w-3.5 h-3.5" />, onClick: () => handleOpenEdit(row) },
              'divider',
              { label: 'Delete Customer', icon: <Trash2 className="w-3.5 h-3.5" />, danger: true, onClick: () => setDeleteTarget(row) },
            ]} />
          )}
        />
      </div>

      {/* ── Customer Purchase History Modal ── */}
      {viewingCustomer && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-2xl w-full p-6 space-y-4 shadow-2xl max-h-[90vh] flex flex-col">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3 flex-shrink-0">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
                  <ShoppingBag className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-bold text-base text-slate-100 flex items-center gap-2">
                    {viewingCustomer.name}
                  </h3>
                  <div className="flex items-center gap-3 text-xs text-slate-400 mt-0.5">
                    {viewingCustomer.phone && <span>Ph: <strong className="font-mono text-slate-300">{viewingCustomer.phone}</strong></span>}
                    {viewingCustomer.address && <span>• {viewingCustomer.address}</span>}
                  </div>
                </div>
              </div>
              <button onClick={() => setViewingCustomer(null)} className="text-slate-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Lifetime Stats */}
            <div className="grid grid-cols-4 gap-3 text-xs flex-shrink-0">
              <div className="p-3 bg-slate-950/60 rounded-xl border border-slate-800 text-center">
                <span className="text-slate-500 block">Total Orders</span>
                <span className="text-lg font-bold text-slate-100 font-mono mt-0.5 block">{customerStats.totalOrders}</span>
              </div>
              <div className="p-3 bg-slate-950/60 rounded-xl border border-slate-800 text-center">
                <span className="text-slate-500 block">Total Spent</span>
                <span className="text-lg font-bold text-emerald-400 font-mono mt-0.5 block">{formatPaise(customerStats.totalSpent)}</span>
              </div>
              <div className="p-3 bg-slate-950/60 rounded-xl border border-slate-800 text-center">
                <span className="text-slate-500 block">Average Order</span>
                <span className="text-lg font-bold text-slate-100 font-mono mt-0.5 block">{formatPaise(customerStats.avgBill)}</span>
              </div>
              <div className="p-3 bg-slate-950/60 rounded-xl border border-slate-800 text-center">
                <span className="text-slate-500 block">Last Purchase</span>
                <span className="text-xs font-bold text-slate-300 font-mono mt-1 block truncate">
                  {customerStats.lastOrder ? formatDate(customerStats.lastOrder) : 'None'}
                </span>
              </div>
            </div>

            {/* Invoices List */}
            <div className="flex-1 overflow-y-auto border border-slate-800 rounded-xl bg-slate-950/40">
              <table className="w-full text-left border-collapse text-xs">
                <thead className="bg-slate-900/90 text-[11px] font-semibold text-slate-400 uppercase tracking-wider sticky top-0 border-b border-slate-800">
                  <tr>
                    <th className="py-2.5 px-4">Invoice #</th>
                    <th className="py-2.5 px-4">Date</th>
                    <th className="py-2.5 px-4">Payment</th>
                    <th className="py-2.5 px-4 text-center">Status</th>
                    <th className="py-2.5 px-4 text-right">Amount</th>
                    <th className="py-2.5 px-4 text-right w-20">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-mono">
                  {loadingCustomerSales ? (
                    <tr>
                      <td colSpan="6" className="py-12 text-center text-slate-500">
                        <div className="inline-block w-4 h-4 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
                      </td>
                    </tr>
                  ) : customerSales.length === 0 ? (
                    <tr>
                      <td colSpan="6" className="py-12 text-center text-slate-500 font-sans">
                        No purchase history found for this customer.
                      </td>
                    </tr>
                  ) : (
                    customerSales.map((sale) => (
                      <tr key={sale.id} className="hover:bg-slate-800/30 transition-colors">
                        <td className="py-2.5 px-4 font-bold text-slate-200">{sale.invoice_number}</td>
                        <td className="py-2.5 px-4 text-slate-400 text-[11px]">{formatDateTime(sale.created_at)}</td>
                        <td className="py-2.5 px-4">
                          <span className="px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 text-[10px] uppercase font-sans">
                            {sale.payment_method}
                          </span>
                        </td>
                        <td className="py-2.5 px-4 text-center">
                          <span className={`inline-flex px-2 py-0.5 rounded-full text-[10px] font-bold ${
                            sale.status === 'CANCELLED' ? 'bg-rose-500/15 text-rose-400' : 'bg-emerald-500/15 text-emerald-400'
                          }`}>
                            {sale.status}
                          </span>
                        </td>
                        <td className="py-2.5 px-4 text-right font-bold text-slate-100">{formatPaise(sale.total_paise)}</td>
                        <td className="py-2.5 px-4 text-right">
                          <button
                            onClick={() => {
                              handleOpenBillDetail(sale);
                              setViewingCustomer(null);
                            }}
                            className="px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white text-[11px] font-sans inline-flex items-center gap-1 transition-colors"
                          >
                            <span>View</span>
                            <ArrowRight className="w-3 h-3 text-emerald-400" />
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            <div className="flex justify-end pt-2 border-t border-slate-800 flex-shrink-0">
              <button
                onClick={() => setViewingCustomer(null)}
                className="px-4 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:bg-slate-800"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add / Edit Customer Modal */}
      {modalOpen && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-2xl">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <h3 className="font-bold text-sm text-slate-100">
                {editingCustomer ? 'Edit Customer' : 'New Customer'}
              </h3>
              <button onClick={() => setModalOpen(false)} className="text-slate-400 hover:text-white">
                <X className="w-4 h-4" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 block mb-1">Name <span className="text-rose-500">*</span></label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="John Doe"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </div>
              <div>
                <label className="text-slate-400 block mb-1">Phone</label>
                <input
                  type="text"
                  value={formData.phone}
                  onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                  placeholder="e.g. 9876543210"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono"
                />
              </div>
              <div>
                <label className="text-slate-400 block mb-1">Address</label>
                <textarea
                  rows="2"
                  value={formData.address}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  placeholder="Store / Home location"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="px-3 py-1.5 rounded-lg font-semibold text-slate-400 hover:bg-slate-800"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  className="px-4 py-1.5 rounded-lg font-bold bg-emerald-500 text-slate-950 hover:bg-emerald-400 disabled:opacity-60"
                >
                  {editingCustomer ? 'Update' : 'Save'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {deleteTarget && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-2xl">
            <h3 className="font-bold text-sm text-slate-100 flex items-center gap-2">
              <Trash2 className="w-4 h-4 text-rose-400" />
              Delete Customer
            </h3>
            <p className="text-xs text-slate-300">
              Are you sure you want to delete <span className="font-semibold text-white">"{deleteTarget.name}"</span>?
              This customer will be soft-deleted and removed from the active directory.
            </p>
            <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
              <button
                type="button"
                onClick={() => setDeleteTarget(null)}
                className="px-3 py-1.5 rounded-lg font-semibold text-slate-400 hover:bg-slate-800 text-xs"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => deleteMutation.mutate(deleteTarget.id)}
                disabled={deleteMutation.isPending}
                className="px-4 py-1.5 rounded-lg font-bold bg-rose-500 text-white hover:bg-rose-400 text-xs disabled:opacity-60"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
