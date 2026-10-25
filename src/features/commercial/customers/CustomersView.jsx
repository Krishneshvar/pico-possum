import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Users, Plus, Phone, MapPin, X } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatDateTime } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';

export default function CustomersView() {
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [formData, setFormData] = useState({ name: '', phone: '', address: '' });

  const { data: customers = [], isLoading } = useQuery({
    queryKey: ['customers'],
    queryFn: () => tauriApi.getCustomers(null),
  });

  const createMutation = useMutation({
    mutationFn: (payload) => tauriApi.createCustomer(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      setModalOpen(false);
      setFormData({ name: '', phone: '', address: '' });
    },
    onError: (err) => alert(`Error: ${err}`),
  });

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.name.trim()) return;
    createMutation.mutate({
      name: formData.name.trim(),
      phone: formData.phone.trim() || null,
      address: formData.address.trim() || null,
    });
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
            <p className="text-[11px] text-slate-500">Manage customer contacts and profiles</p>
          </div>
        </div>
        <button
          onClick={() => setModalOpen(true)}
          className="px-3.5 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-1.5 shadow-lg shadow-emerald-500/20 transition-all"
        >
          <Plus className="w-3.5 h-3.5" />
          Add Customer
        </button>
      </div>

      {/* Data Table */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          data={customers}
          keyFn={(r) => r.id}
          searchKeys={['name', 'phone', 'address']}
          isLoading={isLoading}
          emptyMessage="No customers found."
        />
      </div>

      {/* Add Customer Modal */}
      {modalOpen && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-2xl">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <h3 className="font-bold text-sm text-slate-100">New Customer</h3>
              <button onClick={() => setModalOpen(false)} className="text-slate-400 hover:text-white">
                <X className="w-4 h-4" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 block mb-1">Name *</label>
                <input type="text" required value={formData.name} onChange={(e) => setFormData({ ...formData, name: e.target.value })} placeholder="e.g. Anand Sharma" className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none" />
              </div>
              <div>
                <label className="text-slate-400 block mb-1">Phone</label>
                <input type="text" value={formData.phone} onChange={(e) => setFormData({ ...formData, phone: e.target.value })} placeholder="e.g. 9876543210" className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono" />
              </div>
              <div>
                <label className="text-slate-400 block mb-1">Address</label>
                <textarea rows="2" value={formData.address} onChange={(e) => setFormData({ ...formData, address: e.target.value })} placeholder="Store / Home location" className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none" />
              </div>
              <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
                <button type="button" onClick={() => setModalOpen(false)} className="px-3 py-1.5 rounded-lg font-semibold text-slate-400 hover:bg-slate-800">Cancel</button>
                <button type="submit" disabled={createMutation.isPending} className="px-4 py-1.5 rounded-lg font-bold bg-emerald-500 text-slate-950 hover:bg-emerald-400 disabled:opacity-60">Save</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
