import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { X } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';

const EMPTY_FORM = { name: '', phone: '', address: '' };

export default function AddCustomerModal({ open, onClose, onCreated }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(EMPTY_FORM);

  if (!open) return null;

  const handleClose = () => {
    setForm(EMPTY_FORM);
    onClose?.();
  };

  const handleSave = async () => {
    if (!form.name.trim()) {
      toast.warning('Customer name is required');
      return;
    }
    try {
      const saved = await tauriApi.createCustomer(form);
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      setForm(EMPTY_FORM);
      onCreated?.(saved);
      onClose?.();
      toast.success(`Customer "${saved.name}" added and attached to this bill`);
    } catch (err) {
      toast.error(`Failed to add customer: ${err}`, { title: 'Save failed' });
    }
  };

  return (
    <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-2xl">
        <div className="flex justify-between items-center border-b border-slate-800 pb-3">
          <h3 className="font-bold text-sm text-slate-100">Add New Customer</h3>
          <button onClick={handleClose} className="text-slate-400 hover:text-white">
            <X className="w-4 h-4" />
          </button>
        </div>
        <div className="space-y-3 text-xs">
          <div>
            <label className="text-slate-400 block mb-1">Customer Name <span className="text-rose-500">*</span></label>
            <input
              type="text"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="e.g. Ramesh Kumar"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
            />
          </div>
          <div>
            <label className="text-slate-400 block mb-1">Phone Number</label>
            <input
              type="text"
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
              placeholder="e.g. 9876543210"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
            />
          </div>
          <div>
            <label className="text-slate-400 block mb-1">Address</label>
            <textarea
              rows="2"
              value={form.address}
              onChange={(e) => setForm({ ...form, address: e.target.value })}
              placeholder="Street / locality"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
            />
          </div>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button
            onClick={handleClose}
            className="px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:bg-slate-800"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            className="px-4 py-1.5 rounded-lg text-xs font-bold bg-emerald-500 text-slate-950 hover:bg-emerald-400"
          >
            Save Customer
          </button>
        </div>
      </div>
    </div>
  );
}
