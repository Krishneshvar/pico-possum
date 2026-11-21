import { useState, useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { X } from 'lucide-react';
import Select from '../../../components/common/Select';
import { tauriApi } from '../../../lib/tauri';
import { strings } from '../../../lib/strings';
import { toast } from '../../../stores/toast.store';
import { unitsToCents, getCurrencySymbol } from '../../../lib/formatters';
import { validateProductForm } from '../../../lib/validation';

const DEFAULT_FORM = {
  name: '',
  sku: '',
  categoryId: '',
  mrp: '',
  costPrice: '',
  taxRate: '0',
  initialStock: '0',
  status: 'active',
  minStockAlert: '5',
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
  const [errors, setErrors] = useState({});
  const nameInputRef = useRef(null);

  const isCreate = !product?.id;
  const autoNumericSku = generalSettings?.autoNumericSku ?? generalSettings?.auto_numeric_sku ?? true;
  const allowNegativeStock = generalSettings?.allowNegativeStock ?? generalSettings?.allow_negative_stock ?? false;
  const currencySymbol = generalSettings?.currencySymbol || getCurrencySymbol();

  const { data: nextSku } = useQuery({
    queryKey: ['nextSku'],
    queryFn: () => tauriApi.getNextSku(),
    enabled: Boolean(open && isCreate && autoNumericSku),
  });

  useEffect(() => {
    if (open) {
      const timer = setTimeout(() => {
        nameInputRef.current?.focus();
        nameInputRef.current?.select?.();
      }, 50);
      return () => clearTimeout(timer);
    }
  }, [open]);

  useEffect(() => {
    setErrors({});
    if (product) {
      const mrp = product.mrpCents ?? product.mrp_cents ?? 0;
      const costPrice = product.costPriceCents ?? product.cost_price_cents ?? 0;
      const taxRate = product.taxRate ?? product.tax_rate ?? 0;
      const currentStock = product.currentStock ?? product.current_stock ?? 0;
      const minStockAlert = product.minStockAlert ?? product.min_stock_alert ?? 5;
      const categoryId = product.categoryId ?? product.category_id ?? '';

      setFormData({
        name: product.name || '',
        sku: product.sku || '',
        categoryId,
        mrp: (mrp / 100).toFixed(2),
        costPrice: (costPrice / 100).toFixed(2),
        taxRate: taxRate.toString(),
        initialStock: currentStock.toString(),
        status: product.status || 'active',
        minStockAlert: minStockAlert.toString(),
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

  const handleChange = (field, value) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: undefined }));
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const validation = validateProductForm(formData, isCreate);
    if (!validation.isValid) {
      setErrors(validation.errors);
      const firstError = Object.values(validation.errors)[0];
      if (firstError) toast.warning(firstError);
      return;
    }

    const minStockAlertVal = parseInt(formData.minStockAlert, 10);
    const mrpCents = unitsToCents(formData.mrp);
    const costPriceCents = unitsToCents(formData.costPrice);
    const payload = {
      name: formData.name.trim(),
      sku: formData.sku.trim() || null,
      categoryId: formData.categoryId || null,
      mrpCents,
      costPriceCents,
      taxRate: parseFloat(formData.taxRate) || 0,
      status: formData.status || 'active',
      minStockAlert: !isNaN(minStockAlertVal) && minStockAlertVal >= 0 ? minStockAlertVal : 5,
    };

    if (isCreate) {
      payload.initialStock = parseInt(formData.initialStock, 10) || 0;
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
                ref={nameInputRef}
                type="text"
                required
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                placeholder="e.g. Fresh Milk 500ml"
                className={`w-full bg-surface-secondary border rounded-lg p-2 text-text-primary focus:outline-none ${
                  errors.name ? 'border-danger focus:border-danger' : 'border-border-strong focus:border-brand'
                }`}
              />
              {errors.name && <p className="text-[11px] text-danger mt-1">{errors.name}</p>}
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
                  onChange={(e) => handleChange('sku', e.target.value)}
                  disabled={autoNumericSku}
                  placeholder={autoNumericSku ? 'Generating SKU…' : 'e.g. 100201'}
                  className={`w-full bg-surface-secondary border rounded-lg p-2 text-text-primary font-mono focus:outline-none ${
                    errors.sku ? 'border-danger focus:border-danger' : 'border-border-strong focus:border-brand'
                  } ${autoNumericSku ? 'opacity-70 cursor-not-allowed' : ''}`}
                />
                {errors.sku && <p className="text-[11px] text-danger mt-1">{errors.sku}</p>}
              </div>
              <div>
                <label className="text-text-secondary block font-semibold mb-1">
                  {strings.products.form.category}
                </label>
                <Select
                  value={formData.categoryId}
                  onChange={(val) => setFormData({ ...formData, categoryId: val })}
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
                  value={formData.costPrice}
                  onChange={(e) => handleChange('costPrice', e.target.value)}
                  placeholder={strings.products.form.placeholders.zeroDecimal}
                  className={`w-full bg-surface-secondary border rounded-lg p-2 text-text-primary font-mono focus:outline-none ${
                    errors.costPrice ? 'border-danger focus:border-danger' : 'border-border-strong focus:border-brand'
                  }`}
                />
                {errors.costPrice && <p className="text-[10px] text-danger mt-1">{errors.costPrice}</p>}
              </div>
              <div>
                <label className="text-text-secondary block font-semibold mb-1">
                  {strings.products.form.mrp(currencySymbol)} <span className="text-danger">*</span>
                </label>
                <input
                  type="number"
                  step="0.01"
                  required
                  value={formData.mrp}
                  onChange={(e) => handleChange('mrp', e.target.value)}
                  placeholder={strings.products.form.placeholders.mrp}
                  className={`w-full bg-surface-secondary border rounded-lg p-2 text-text-primary font-mono font-semibold focus:outline-none ${
                    errors.mrp ? 'border-danger focus:border-danger' : 'border-border-strong focus:border-brand'
                  }`}
                />
                {errors.mrp && <p className="text-[10px] text-danger mt-1">{errors.mrp}</p>}
              </div>
              <div>
                <label className="text-text-secondary block font-semibold mb-1">
                  {strings.products.form.gstTaxRate}
                </label>
                <input
                  type="number"
                  step="0.1"
                  value={formData.taxRate}
                  onChange={(e) => handleChange('taxRate', e.target.value)}
                  placeholder={strings.products.form.placeholders.zero}
                  className={`w-full bg-surface-secondary border rounded-lg p-2 text-text-primary font-mono focus:outline-none ${
                    errors.taxRate ? 'border-danger focus:border-danger' : 'border-border-strong focus:border-brand'
                  }`}
                />
                {errors.taxRate && <p className="text-[10px] text-danger mt-1">{errors.taxRate}</p>}
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
                  value={formData.minStockAlert}
                  onChange={(e) => handleChange('minStockAlert', e.target.value)}
                  disabled={allowNegativeStock}
                  placeholder={strings.products.form.placeholders.five}
                  className={`w-full bg-surface-secondary border rounded-lg p-2 text-text-primary font-mono focus:outline-none ${
                    errors.minStockAlert ? 'border-danger focus:border-danger' : 'border-border-strong focus:border-brand'
                  } ${allowNegativeStock ? 'opacity-50 cursor-not-allowed' : ''}`}
                />
                {errors.minStockAlert && <p className="text-[10px] text-danger mt-1">{errors.minStockAlert}</p>}
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
                  value={formData.initialStock}
                  onChange={(e) => handleChange('initialStock', e.target.value)}
                  placeholder="0"
                  className={`w-full bg-surface-secondary border rounded-lg p-2 text-text-primary font-mono focus:outline-none ${
                    errors.initialStock ? 'border-danger focus:border-danger' : 'border-border-strong focus:border-brand'
                  }`}
                />
                {errors.initialStock && <p className="text-[10px] text-danger mt-1">{errors.initialStock}</p>}
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
