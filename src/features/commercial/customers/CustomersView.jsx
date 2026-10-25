import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Users, Plus, Search, Phone, MapPin, X } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatDateTime } from '../../../lib/formatters';

export default function CustomersView() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [formData, setFormData] = useState({ name: '', phone: '', address: '' });

  const { data: customers = [], isLoading } = useQuery({
    queryKey: ['customers', search],
    queryFn: () => tauriApi.getCustomers(search || null),
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

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-16 px-6 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <Users className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-100">Customer Directory</h2>
            <p className="text-xs text-slate-400">Manage customer contacts and profiles</p>
          </div>
        </div>

        <button
          onClick={() => setModalOpen(true)}
          className="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-emerald-500/20 transition-all"
        >
          <Plus className="w-4 h-4" />
          <span>Add Customer</span>
        </button>
      </div>

      {/* Filter Bar */}
      <div className="p-4 border-b border-slate-800/80 bg-slate-900/30 flex items-center gap-4">
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            type="text"
            placeholder="Search by customer name or phone..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-9 pr-4 py-2 rounded-xl bg-slate-900 border border-slate-800 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-emerald-500"
          />
        </div>
      </div>

      {/* Customers List */}
      <div className="flex-1 overflow-y-auto p-6">
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
          {customers.map((c) => (
            <div
              key={c.id}
              className="glass-card p-4 rounded-xl border border-slate-800 space-y-2 hover:border-slate-700 transition-all"
            >
              <div className="flex items-center justify-between">
                <h4 className="font-bold text-sm text-slate-100">{c.name}</h4>
                <span className="text-[10px] text-emerald-400 font-mono bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/20">
                  Customer
                </span>
              </div>
              <div className="space-y-1 text-xs text-slate-400">
                <div className="flex items-center gap-2">
                  <Phone className="w-3.5 h-3.5 text-slate-500" />
                  <span className="font-mono">{c.phone || 'No phone'}</span>
                </div>
                {c.address && (
                  <div className="flex items-start gap-2">
                    <MapPin className="w-3.5 h-3.5 text-slate-500 shrink-0 mt-0.5" />
                    <span className="truncate">{c.address}</span>
                  </div>
                )}
              </div>
              <div className="pt-2 border-t border-slate-800/60 text-[11px] text-slate-500 flex justify-between">
                <span>Joined</span>
                <span>{formatDateTime(c.created_at)}</span>
              </div>
            </div>
          ))}
        </div>
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
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="e.g. Anand Sharma"
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
                  disabled={createMutation.isPending}
                  className="px-4 py-1.5 rounded-lg font-bold bg-emerald-500 text-slate-950 hover:bg-emerald-400"
                >
                  Save
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
