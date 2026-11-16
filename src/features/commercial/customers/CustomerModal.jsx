import { useState, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { X, UserPlus, UserCheck } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { strings } from '../../../lib/strings';

const EMPTY_FORM = { name: '', phone: '', address: '' };

export default function CustomerModal({
  open,
  onClose,
  customer = null,
  onSaved,
  title,
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(EMPTY_FORM);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (customer) {
      setForm({
        name: customer.name || '',
        phone: customer.phone || '',
        address: customer.address || '',
      });
    } else {
      setForm(EMPTY_FORM);
    }
  }, [customer, open]);

  if (!open) return null;

  const isEditing = Boolean(customer?.id);

  const handleClose = () => {
    setForm(EMPTY_FORM);
    onClose?.();
  };

  const handleSubmit = async (e) => {
    e?.preventDefault();
    const trimmedName = form.name.trim();
    if (!trimmedName) {
      toast.warning(strings.customers.toasts.nameRequired);
      return;
    }

    const payload = {
      name: trimmedName,
      phone: form.phone.trim() || null,
      address: form.address.trim() || null,
    };

    setIsSubmitting(true);
    try {
      let saved;
      if (isEditing) {
        saved = await tauriApi.updateCustomer(customer.id, payload);
        toast.success(strings.customers.toasts.customerUpdated);
      } else {
        saved = await tauriApi.createCustomer(payload);
        toast.success(strings.customers.toasts.customerCreated);
      }
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      setForm(EMPTY_FORM);
      onSaved?.(saved);
      onClose?.();
    } catch (err) {
      const msg = typeof err === 'string' ? err : err?.message || 'Failed to save customer';
      toast.error(msg, {
        title: isEditing
          ? strings.customers.toasts.updateFailedTitle
          : strings.customers.toasts.createFailedTitle,
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  const modalTitle =
    title ||
    (isEditing
      ? strings.customers.form.editTitle
      : strings.customers.form.addTitle);

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
      <div className="bg-surface border border-border-default rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-xl text-text-primary animate-scale-in">
        <div className="flex justify-between items-center border-b border-border-default pb-3">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-brand-subtle text-brand flex items-center justify-center">
              {isEditing ? <UserCheck className="w-4 h-4" /> : <UserPlus className="w-4 h-4" />}
            </div>
            <h3 className="font-bold text-sm text-text-primary">{modalTitle}</h3>
          </div>
          <button
            type="button"
            onClick={handleClose}
            className="p-1 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-secondary cursor-pointer transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-3 text-xs">
          <div>
            <label className="text-text-secondary block font-semibold mb-1">
              {strings.customers.form.name} <span className="text-danger">*</span>
            </label>
            <input
              type="text"
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder={strings.customers.form.namePlaceholder}
              className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none"
            />
          </div>

          <div>
            <label className="text-text-secondary block font-semibold mb-1">
              {strings.customers.form.phone}
            </label>
            <input
              type="text"
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
              placeholder={strings.customers.form.phonePlaceholder}
              className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none font-mono"
            />
          </div>

          <div>
            <label className="text-text-secondary block font-semibold mb-1">
              {strings.customers.form.address}
            </label>
            <textarea
              rows="2"
              value={form.address}
              onChange={(e) => setForm({ ...form, address: e.target.value })}
              placeholder={strings.customers.form.addressPlaceholder}
              className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none resize-none"
            />
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t border-border-default">
            <button
              type="button"
              onClick={handleClose}
              className="px-3 py-1.5 rounded-lg font-semibold text-text-secondary hover:bg-surface-secondary border border-border-default cursor-pointer"
            >
              {strings.common.actions.cancel}
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-4 py-1.5 rounded-lg font-bold bg-brand text-white hover:bg-brand-hover shadow-xs disabled:opacity-50 cursor-pointer"
            >
              {isEditing
                ? strings.customers.form.updateCustomer
                : strings.customers.form.saveCustomer}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
