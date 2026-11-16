import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { X } from 'lucide-react';
import Select from '../../../components/common/Select';
import { tauriApi } from '../../../lib/tauri';
import { strings } from '../../../lib/strings';
import { toast } from '../../../stores/toast.store';
import { rupeesToPaise, getCurrencySymbol } from '../../../lib/formatters';

const DEFAULT_FORM = {
  name: '',
  sku: '',
  category_id: '',
  mrp_rupees: '',
  cost_price_rupees: '',
  tax_rate: '0',
  initial_stock: '0',
  status: 'active',
  min_stock_alert: '5',
  uom: 'Piece',
};

export default function ProductModal({
  open,
  onClose,
  product = null,
  categories = [],
  generalSettings = null,
  onSubmit,
  isPending = false,
}) {
  const [formData, setFormData] = useState(DEFAULT_FORM);

  const isCreate = !product?.id;
  const autoNumericSku = generalSettings?.auto_numeric_sku ?? true;
  const allowNegativeStock = generalSettings?.allow_negative_stock ?? false;
  const currencySymbol = generalSettings?.currency_symbol || getCurrencySymbol();

  const { data: nextSku } = useQuery({
    queryKey: ['next_sku'],
    queryFn: () => tauriApi.getNextSku(),
    enabled: Boolean(open && isCreate && autoNumericSku),
  });

  useEffect(() => {
    if (product) {
      setFormData({
        name: product.name || '',
        sku: product.sku || '',
        category_id: product.category_id || '',
        mrp_rupees: (product.mrp_paise / 100).toFixed(2),
        cost_price_rupees: (product.cost_price_paise / 100).toFixed(2),
        tax_rate: (product.tax_rate ?? 0).toString(),
        initial_stock: (product.current_stock ?? 0).toString(),
        status: product.status || 'active',
        min_stock_alert: (product.min_stock_alert != null ? product.min_stock_alert : 5).toString(),
        uom: product.uom || 'Piece',
      });
    } else {
      setFormData({
        ...DEFAULT_FORM,
        sku: nextSku || '',
      });
    }
  }, [product, open, nextSku]);

  if (!open) return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.name.trim()) {
      toast.warning(strings.products.validation.nameRequired);
      return;
    }
    const minStockAlertVal = parseInt(formData.min_stock_alert, 10);
    const payload = {
      name: formData.name.trim(),
      sku: formData.sku.trim() || null,
      category_id: formData.category_id || null,
      mrp_paise: rupeesToPaise(formData.mrp_rupees),
      cost_price_paise: rupeesToPaise(formData.cost_price_rupees),
      tax_rate: parseFloat(formData.tax_rate) || 0,
      status: formData.status || 'active',
      min_stock_alert: !isNaN(minStockAlertVal) && minStockAlertVal >= 0 ? minStockAlertVal : 5,
    };

    if (isCreate) {
      payload.initial_stock = parseInt(formData.initial_stock, 10) || 0;
    }

    onSubmit(payload, isCreate);
  };

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
      <div className="bg-surface border border-border-default rounded-2xl max-w-lg w-full p-6 shadow-xl space-y-4 text-text-primary animate-scale-in">
        <div className="flex justify-between items-center border-b border-border-default pb-3">
          <h3 className="font-bold text-sm text-text-primary">
            {isCreate ? 'Add New Product' : 'Edit Product'}
          </h3>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-secondary cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 text-xs">
          {/* Group 1: Product Information */}
          <div className="space-y-2.5">
            <span className="text-[11px] font-bold text-text-muted uppercase tracking-wider block">
              Product Information
            </span>
            <div>
              <label className="text-text-secondary block font-semibold mb-1">
                Product Name <span className="text-danger">*</span>
              </label>
              <input
                type="text"
                required
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder="e.g. Fresh Milk 500ml"
                className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-text-secondary block font-semibold mb-1">
                  SKU / Barcode
                  {autoNumericSku && (
                    <span className="ml-1.5 text-[10px] font-bold text-brand bg-brand-subtle px-1.5 py-0.5 rounded">
                      Auto
                    </span>
                  )}
                </label>
                <input
                  type="text"
                  value={formData.sku}
                  onChange={(e) => setFormData({ ...formData, sku: e.target.value })}
                  disabled={autoNumericSku}
                  placeholder={autoNumericSku ? 'Generating SKU…' : 'e.g. 100201'}
                  className={`w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary font-mono focus:border-brand focus:outline-none ${
                    autoNumericSku ? 'opacity-70 cursor-not-allowed' : ''
                  }`}
                />
              </div>
              <div>
                <label className="text-text-secondary block font-semibold mb-1">
                  {strings.products.form.category}
                </label>
                <Select
                  value={formData.category_id}
                  onChange={(val) => setFormData({ ...formData, category_id: val })}
                  placeholder={strings.products.form.noCategory}
                  options={[
                    { value: '', label: strings.products.form.noCategory },
                    ...categories.map((c) => ({ value: String(c.id), label: c.name })),
                  ]}
                />
              </div>
            </div>
          </div>

          {/* Group 2: Pricing and Tax */}
          <div className="space-y-2.5 pt-2 border-t border-border-default">
            <span className="text-[11px] font-bold text-text-muted uppercase tracking-wider block">
              {strings.products.form.sections.pricing}
            </span>
            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className="text-text-secondary block font-semibold mb-1">
                  {strings.products.form.costPrice(currencySymbol)}
                </label>
                <input
                  type="number"
                  step="0.01"
                  value={formData.cost_price_rupees}
                  onChange={(e) => setFormData({ ...formData, cost_price_rupees: e.target.value })}
                  placeholder={strings.products.form.placeholders.zeroDecimal}
                  className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary font-mono focus:border-brand focus:outline-none"
                />
              </div>
              <div>
                <label className="text-text-secondary block font-semibold mb-1">
                  {strings.products.form.mrp(currencySymbol)} <span className="text-danger">*</span>
                </label>
                <input
                  type="number"
                  step="0.01"
                  required
                  value={formData.mrp_rupees}
                  onChange={(e) => setFormData({ ...formData, mrp_rupees: e.target.value })}
                  placeholder={strings.products.form.placeholders.mrp}
                  className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary font-mono font-semibold focus:border-brand focus:outline-none"
                />
              </div>
              <div>
                <label className="text-text-secondary block font-semibold mb-1">
                  {strings.products.form.gstTaxRate}
                </label>
                <input
                  type="number"
                  step="0.1"
                  value={formData.tax_rate}
                  onChange={(e) => setFormData({ ...formData, tax_rate: e.target.value })}
                  placeholder={strings.products.form.placeholders.zero}
                  className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary font-mono focus:border-brand focus:outline-none"
                />
              </div>
            </div>
          </div>

          {/* Group 3: Inventory Configuration */}
          <div className="space-y-2.5 pt-2 border-t border-border-default">
            <span className="text-[11px] font-bold text-text-muted uppercase tracking-wider block">
              {strings.products.form.sections.inventory}
            </span>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-text-secondary block font-semibold mb-1">
                  {strings.products.form.lowStockAlert}
                </label>
                <input
                  type="number"
                  min="0"
                  value={formData.min_stock_alert}
                  onChange={(e) => setFormData({ ...formData, min_stock_alert: e.target.value })}
                  disabled={allowNegativeStock}
                  placeholder={strings.products.form.placeholders.five}
                  className={`w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary font-mono focus:border-brand focus:outline-none ${
                    allowNegativeStock ? 'opacity-50 cursor-not-allowed' : ''
                  }`}
                />
              </div>
              <div>
                <label className="text-text-secondary block font-semibold mb-1">
                  {strings.products.form.productStatus}
                </label>
                <Select
                  value={formData.status}
                  onChange={(val) => setFormData({ ...formData, status: val })}
                  options={[
                    { value: 'active', label: strings.products.form.statusOptions.active },
                    { value: 'inactive', label: strings.products.form.statusOptions.inactive },
                    { value: 'discontinued', label: strings.products.form.statusOptions.discontinued },
                  ]}
                />
              </div>
            </div>

            {isCreate && (
              <div>
                <label className="text-text-secondary block font-semibold mb-1">Initial Opening Stock (Units)</label>
                <input
                  type="number"
                  value={formData.initial_stock}
                  onChange={(e) => setFormData({ ...formData, initial_stock: e.target.value })}
                  placeholder="0"
                  className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary font-mono focus:border-brand focus:outline-none"
                />
                <span className="text-[10px] text-text-muted mt-1 block">
                  Recorded as initial stock balance in the stock movements ledger.
                </span>
              </div>
            )}
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t border-border-default">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-1.5 rounded-lg font-semibold text-text-secondary hover:bg-surface-secondary border border-border-default cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isPending}
              className="px-4 py-1.5 rounded-lg font-bold bg-brand text-white hover:bg-brand-hover shadow-xs disabled:opacity-50 cursor-pointer"
            >
              {isCreate ? 'Create Product' : 'Save'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
