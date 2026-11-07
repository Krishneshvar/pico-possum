import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { X, UserPlus } from 'lucide-react';
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

  const handleSave = async (e) => {
    e?.preventDefault();
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
      toast.success(`Customer "${saved.name}" attached to bill`);
    } catch (err) {
      toast.error(`Failed to add customer: ${err}`, { title: 'Save failed' });
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
      <div className="bg-surface border border-border-default rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-xl animate-scale-in">
        <div className="flex justify-between items-center border-b border-border-default pb-3">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-brand-subtle text-brand flex items-center justify-center">
              <UserPlus className="w-4 h-4" />
            </div>
            <h3 className="font-bold text-sm text-text-primary">Add New Customer</h3>
          </div>
          <button
            type="button"
            onClick={handleClose}
            className="p-1 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSave} className="space-y-3 text-xs">
          <div>
            <label className="text-text-secondary block font-semibold mb-1">
              Customer Name <span className="text-danger">*</span>
            </label>
            <input
              type="text"
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="e.g. Rajesh Sharma"
              className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none"
            />
          </div>
          <div>
            <label className="text-text-secondary block font-semibold mb-1">Phone Number</label>
            <input
              type="text"
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
              placeholder="e.g. 9876543210"
              className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none"
            />
          </div>
          <div>
            <label className="text-text-secondary block font-semibold mb-1">Address / Notes</label>
            <textarea
              rows="2"
              value={form.address}
              onChange={(e) => setForm({ ...form, address: e.target.value })}
              placeholder="Street / Locality / GSTIN"
              className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none resize-none"
            />
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t border-border-default">
            <button
              type="button"
              onClick={handleClose}
              className="px-3 py-1.5 rounded-lg text-xs font-semibold text-text-secondary hover:bg-surface-secondary cursor-pointer border border-border-default"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="px-4 py-1.5 rounded-lg text-xs font-bold bg-brand text-white hover:bg-brand-hover shadow-xs cursor-pointer"
            >
              Save Customer
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
