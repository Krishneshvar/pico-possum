import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Package, Plus, Edit2, Trash2, X, ArrowUpDown } from 'lucide-react';
import DropdownActions from '../../../components/common/DropdownActions';
import ImportCsvButton from '../../../components/common/ImportCsvButton';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';

import { formatPaise, rupeesToPaise, getCurrencySymbol } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';

export default function ProductsView() {
  const queryClient = useQueryClient();
  const currencySymbol = getCurrencySymbol();
  const [modalMode, setModalMode] = useState(null); // 'create' | 'edit' | 'stock'
  const [selectedProduct, setSelectedProduct] = useState(null);

  const [formData, setFormData] = useState({
    name: '', sku: '', category_id: '', mrp_rupees: '',
    cost_price_rupees: '', tax_rate: '0', initial_stock: '0',
    status: 'active', min_stock_alert: '5',
  });
  const [showLowStockOnly, setShowLowStockOnly] = useState(false);
  const [stockAdjustment, setStockAdjustment] = useState({
    quantity_change: 0,
    movement_type: 'RESTOCK',
    note: '',
  });

  const { data: products = [], isLoading } = useQuery({
    queryKey: ['products'],
    queryFn: () => tauriApi.getProducts(null, null),
  });
  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: () => tauriApi.getCategories(),
  });
  const { data: generalSettings } = useQuery({
    queryKey: ['general_settings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });

  const autoNumericSku = generalSettings?.auto_numeric_sku ?? true;
  const settingsLoaded = generalSettings !== undefined;
  const allowNegativeStock = generalSettings?.allow_negative_stock ?? false;

  // Preview of the SKU that will be assigned on create (backend is authoritative).
  const { data: nextSku } = useQuery({
    queryKey: ['next_sku'],
    queryFn: () => tauriApi.getNextSku(),
    enabled: settingsLoaded && autoNumericSku && modalMode === 'create',
  });

  useEffect(() => {
    if (settingsLoaded && modalMode === 'create' && autoNumericSku && nextSku) {
      setFormData((prev) => (prev.sku === nextSku ? prev : { ...prev, sku: nextSku }));
    }
  }, [settingsLoaded, modalMode, autoNumericSku, nextSku]);

  const createMutation = useMutation({
    mutationFn: (p) => tauriApi.createProduct(p),
    onSuccess: (_data, product) => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['next_sku'] });
      setModalMode(null);
      toast.success(`Product "${product.name}" created successfully`);
    },
    onError: (err) => toast.error(`Failed to create product: ${err}`, { title: 'Create failed' }),
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => tauriApi.updateProduct(id, payload),
    onSuccess: (_data, { payload }) => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      setModalMode(null);
      toast.success(`Product "${payload.name}" updated successfully`);
    },
    onError: (err) => toast.error(`Failed to update product: ${err}`, { title: 'Update failed' }),
  });
  const deleteMutation = useMutation({
    mutationFn: (id) => tauriApi.deleteProduct(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      toast.success('Product deleted successfully');
    },
    onError: (err) => toast.error(`Failed to delete product: ${err}`, { title: 'Delete failed' }),
  });
  const adjustStockMutation = useMutation({
    mutationFn: (p) => tauriApi.adjustStock(p),
    onSuccess: (_data, adjustment) => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['stock_movements'] });
      setModalMode(null);
      const delta = adjustment.quantity_change;
      toast.success(
        `${delta > 0 ? 'Added' : 'Removed'} ${Math.abs(delta)} unit${Math.abs(delta) === 1 ? '' : 's'} ${
          delta > 0 ? 'to' : 'from'
        } "${selectedProduct?.name ?? 'product'}"`
      );
    },
    onError: (err) => toast.error(`Failed to adjust stock: ${err}`, { title: 'Stock update failed' }),
  });

  const handleOpenCreate = () => {
    setFormData({
      name: '', sku: '', category_id: '', mrp_rupees: '',
      cost_price_rupees: '', tax_rate: '0', initial_stock: '0',
      status: 'active', min_stock_alert: '5',
    });
    setModalMode('create');
  };
  const handleOpenEdit = (p) => {
    setSelectedProduct(p);
    setFormData({
      name: p.name,
      sku: p.sku || '',
      category_id: p.category_id || '',
      mrp_rupees: (p.mrp_paise / 100).toFixed(2),
      cost_price_rupees: (p.cost_price_paise / 100).toFixed(2),
      tax_rate: p.tax_rate.toString(),
      initial_stock: p.current_stock.toString(),
      status: p.status || 'active',
      min_stock_alert: (p.min_stock_alert !== undefined && p.min_stock_alert !== null ? p.min_stock_alert : 5).toString(),
    });
    setModalMode('edit');
  };
  const handleOpenStock = (p) => {
    setSelectedProduct(p);
    setStockAdjustment({
      quantity_change: 0,
      movement_type: 'RESTOCK',
      note: 'Supplier restock delivery',
    });
    setModalMode('stock');
  };
  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.name.trim()) { toast.warning('Product name is required'); return; }
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
    if (modalMode === 'create') {
      createMutation.mutate({ ...payload, initial_stock: parseInt(formData.initial_stock, 10) || 0 });
    } else {
      updateMutation.mutate({ id: selectedProduct.id, payload });
    }
  };

  // ── DataTable column definitions ─────────────────────────────────────────
  const columns = [
    {
      key: 'sku', label: 'SKU',
      className: 'font-mono text-slate-400',
    },
    {
      key: 'name', label: 'Product Name',
      className: 'font-semibold text-slate-200',
    },
    {
      key: 'category_name', label: 'Category',
      className: 'text-slate-400',
      render: (r) => r.category_name || <span className="text-slate-600">—</span>,
    },
    {
      key: 'mrp_paise', label: 'MRP',
      headerClassName: 'text-right',
      className: 'text-right font-mono font-bold text-slate-100',
      render: (r) => formatPaise(r.mrp_paise),
    },
    {
      key: 'cost_price_paise', label: 'Cost',
      headerClassName: 'text-right',
      className: 'text-right font-mono text-slate-400',
      render: (r) => formatPaise(r.cost_price_paise),
    },
    {
      key: 'tax_rate', label: 'Tax %',
      headerClassName: 'text-center',
      className: 'text-center font-mono text-slate-400',
      render: (r) => `${r.tax_rate}%`,
    },
    {
      key: 'current_stock', label: 'Stock',
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => {
        const s = r.current_stock;
        const alertThreshold = r.min_stock_alert ?? 5;

        // Stock alerts are intentionally muted when the user has allowed
        // negative stock / ignore stock alerts in General Settings.
        if (allowNegativeStock) {
          return (
            <span
              title={`Stock: ${s}`}
              className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-bold font-mono border bg-slate-800/70 text-slate-300 border-slate-700/60"
            >
              {s}
            </span>
          );
        }

        const isOut = s <= 0;
        const isLow = s <= alertThreshold && !isOut;
        const cls = isOut
          ? 'bg-rose-500/15 text-rose-400 border-rose-500/30'
          : isLow
          ? 'bg-amber-500/15 text-amber-400 border-amber-500/30'
          : 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30';
        return (
          <span
            title={isLow ? `Low stock alert (threshold: ${alertThreshold})` : isOut ? 'Out of stock' : `Stock: ${s} (Alert at ≤ ${alertThreshold})`}
            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-bold font-mono border ${cls}`}
          >
            {isLow && <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" />}
            {s}
          </span>
        );
      },
    },
    {
      key: 'status', label: 'Status',
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => {
        const status = r.status || 'active';
        const styles = {
          active: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
          inactive: 'bg-slate-500/15 text-slate-400 border-slate-500/30',
          discontinued: 'bg-rose-500/15 text-rose-400 border-rose-500/30',
        };
        return (
          <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-semibold uppercase tracking-wider border ${styles[status] || styles.active}`}>
            {status}
          </span>
        );
      },
    },
  ];

  const categoryFilterOptions = categories.map((c) => ({ value: String(c.id), label: c.name }));

  const displayProducts = showLowStockOnly && !allowNegativeStock
    ? products.filter((p) => p.current_stock <= (p.min_stock_alert ?? 5))
    : products;

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-14 px-5 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <Package className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-100">Product Catalog</h2>
            <p className="text-[11px] text-slate-500">Manage catalog, pricing, tax rates, alerts, and stock</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <ImportCsvButton
            entity="products"
            onComplete={() => {
              queryClient.invalidateQueries({ queryKey: ['products'] });
              queryClient.invalidateQueries({ queryKey: ['categories'] });
              queryClient.invalidateQueries({ queryKey: ['stock_movements'] });
              queryClient.invalidateQueries({ queryKey: ['next_sku'] });
            }}
          />
          <button
            onClick={handleOpenCreate}
            className="px-3.5 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-1.5 shadow-lg shadow-emerald-500/20 transition-all cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />
            Add Product
          </button>
        </div>
      </div>


      {/* Data Table */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          persistKey="products"
          data={displayProducts}
          keyFn={(r) => r.id}
          searchKeys={['name', 'sku', 'category_name']}
          headerRight={!allowNegativeStock ? (
            <button
              onClick={() => setShowLowStockOnly((v) => !v)}
              className={`px-2.5 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 border transition-all ${
                showLowStockOnly
                  ? 'bg-amber-500/20 text-amber-300 border-amber-500/40 shadow-sm'
                  : 'bg-slate-900 text-slate-400 border-slate-800 hover:text-slate-200'
              }`}
            >
              <span className={`w-2 h-2 rounded-full ${showLowStockOnly ? 'bg-amber-400 animate-pulse' : 'bg-slate-500'}`} />
              Low Stock Only ({products.filter((p) => p.current_stock <= (p.min_stock_alert ?? 5)).length})
            </button>
          ) : undefined}
          filters={[
            {
              type: 'multiselect',
              key: 'category_id',
              label: 'Categories',
              options: categoryFilterOptions,
            },
            {
              type: 'multiselect',
              key: 'status',
              label: 'Status',
              options: [
                { value: 'active', label: 'Active' },
                { value: 'inactive', label: 'Inactive' },
                { value: 'discontinued', label: 'Discontinued' },
              ],
            },
            {
              type: 'range',
              key: 'mrp_paise',
              label: 'Price',
              unit: currencySymbol,
              transform: (v) => Math.round(parseFloat(v) * 100),
            },
          ]}
          isLoading={isLoading}
          emptyMessage="No products found."
          actions={(row) => (
            <DropdownActions items={[
              { label: 'Adjust Stock', icon: <ArrowUpDown className="w-3.5 h-3.5" />, onClick: () => handleOpenStock(row) },
              { label: 'Edit Product', icon: <Edit2 className="w-3.5 h-3.5" />, onClick: () => handleOpenEdit(row) },
              'divider',
              { label: 'Delete Product', icon: <Trash2 className="w-3.5 h-3.5" />, danger: true, onClick: () => { if (confirm(`Delete "${row.name}"?`)) deleteMutation.mutate(row.id); } },
            ]} />
          )}
        />
      </div>

      {/* ── Product Create / Edit Modal ── */}
      {(modalMode === 'create' || modalMode === 'edit') && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 shadow-2xl space-y-4">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <h3 className="font-bold text-sm text-slate-100">
                {modalMode === 'create' ? 'Add New Product' : 'Edit Product'}
              </h3>
              <button onClick={() => setModalMode(null)} className="text-slate-400 hover:text-white"><X className="w-4 h-4" /></button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 block mb-1">Product Name <span className="text-rose-500">*</span></label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="e.g. Mineral Water 500ml"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-slate-400 block mb-1">
                    SKU / Barcode
                    {autoNumericSku && (
                      <span className="ml-1.5 text-[10px] font-semibold text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-1.5 py-0.5 rounded">
                        Auto
                      </span>
                    )}
                  </label>
                  <input
                    type="text"
                    value={formData.sku}
                    onChange={(e) => setFormData({ ...formData, sku: e.target.value })}
                    disabled={autoNumericSku}
                    placeholder={autoNumericSku ? 'Generating…' : 'e.g. 100201'}
                    className={`w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono ${
                      autoNumericSku ? 'opacity-70 cursor-not-allowed' : ''
                    }`}
                  />
                  {autoNumericSku && (
                    <span className="text-[10px] text-slate-500 mt-1 block">
                      {modalMode === 'create'
                        ? 'Assigned automatically from the latest numerical SKU.'
                        : 'SKU is locked while automatic numerical SKUs are enabled.'}
                    </span>
                  )}
                </div>
                <div>
                  <label className="text-slate-400 block mb-1">Category</label>
                  <select
                    value={formData.category_id}
                    onChange={(e) => setFormData({ ...formData, category_id: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                  >
                    <option value="">No Category</option>
                    {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-slate-400 block mb-1">MRP ({currencySymbol}) <span className="text-rose-500">*</span></label>
                  <input
                    type="number"
                    step="0.01"
                    required
                    value={formData.mrp_rupees}
                    onChange={(e) => setFormData({ ...formData, mrp_rupees: e.target.value })}
                    placeholder="100.00"
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono"
                  />
                </div>
                <div>
                  <label className="text-slate-400 block mb-1">Cost Price ({currencySymbol})</label>
                  <input
                    type="number"
                    step="0.01"
                    value={formData.cost_price_rupees}
                    onChange={(e) => setFormData({ ...formData, cost_price_rupees: e.target.value })}
                    placeholder="75.00"
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-slate-400 block mb-1">Tax Rate (%)</label>
                  <input
                    type="number"
                    step="0.1"
                    value={formData.tax_rate}
                    onChange={(e) => setFormData({ ...formData, tax_rate: e.target.value })}
                    placeholder="0"
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono"
                  />
                </div>
                <div>
                  <label className="text-slate-400 block mb-1">
                    Stock Alert Threshold{' '}
                    <span className="text-slate-500 text-[10px]">
                      {allowNegativeStock ? '(ignored while stock alerts are off)' : '(notify when stock ≤)'}
                    </span>
                  </label>
                  <input
                    type="number"
                    min="0"
                    value={formData.min_stock_alert}
                    onChange={(e) => setFormData({ ...formData, min_stock_alert: e.target.value })}
                    disabled={allowNegativeStock}
                    placeholder="5"
                    className={`w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono ${
                      allowNegativeStock ? 'opacity-50 cursor-not-allowed' : ''
                    }`}
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-slate-400 block mb-1">Product Status</label>
                  <select
                    value={formData.status}
                    onChange={(e) => setFormData({ ...formData, status: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                  >
                    <option value="active">Active (Available for sale)</option>
                    <option value="inactive">Inactive (Hidden from POS)</option>
                    <option value="discontinued">Discontinued (End of life)</option>
                  </select>
                </div>
                {modalMode === 'create' ? (
                  <div>
                    <label className="text-slate-400 block mb-1">Initial Stock (units)</label>
                    <input
                      type="number"
                      value={formData.initial_stock}
                      onChange={(e) => setFormData({ ...formData, initial_stock: e.target.value })}
                      placeholder="0"
                      className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono"
                    />
                  </div>
                ) : (
                  <div>
                    <label className="text-slate-400 block mb-1">Current Stock (units)</label>
                    <div className="w-full bg-slate-950/50 border border-slate-800/80 rounded-lg p-2 text-slate-400 font-mono">
                      {selectedProduct?.current_stock ?? 0}
                    </div>
                  </div>
                )}
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-slate-800">
                <button type="button" onClick={() => setModalMode(null)} className="px-4 py-2 rounded-lg font-semibold text-slate-400 hover:bg-slate-800">Cancel</button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  className="px-5 py-2 rounded-lg font-bold bg-emerald-500 text-slate-950 hover:bg-emerald-400 transition-colors shadow-md shadow-emerald-500/20 disabled:opacity-60"
                >
                  {modalMode === 'create' ? 'Create Product' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Stock Adjustment Modal ── */}
      {modalMode === 'stock' && selectedProduct && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-2xl">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <div>
                <h3 className="font-bold text-sm text-slate-100">Adjust Stock</h3>
                <p className="text-[11px] text-slate-400 truncate max-w-[200px]">{selectedProduct.name}</p>
              </div>
              <button onClick={() => setModalMode(null)} className="text-slate-400 hover:text-white"><X className="w-4 h-4" /></button>
            </div>
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-800 text-xs flex justify-between">
              <span className="text-slate-400">Current Stock:</span>
              <strong className="text-slate-100 font-mono text-sm">{selectedProduct.current_stock}</strong>
            </div>
            <div className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 block mb-1">Adjustment Type</label>
                <select
                  value={stockAdjustment.movement_type}
                  onChange={(e) => setStockAdjustment({ ...stockAdjustment, movement_type: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                >
                  <option value="RESTOCK">Restock / Inventory Delivery</option>
                  <option value="PURCHASE">Supplier Purchase</option>
                  <option value="MANUAL_ADJUSTMENT">Manual Stock Adjustment / Correction</option>
                  <option value="RETURN">Customer Return / Exchange</option>
                </select>
              </div>
              <div>
                <label className="text-slate-400 block mb-1">Quantity Change (+ to add, − to deduct)</label>
                <input
                  type="number"
                  value={stockAdjustment.quantity_change}
                  onChange={(e) => setStockAdjustment({ ...stockAdjustment, quantity_change: parseInt(e.target.value, 10) || 0 })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono"
                />
              </div>
              <div>
                <label className="text-slate-400 block mb-1">Reason / Note</label>
                <input
                  type="text"
                  value={stockAdjustment.note}
                  onChange={(e) => setStockAdjustment({ ...stockAdjustment, note: e.target.value })}
                  placeholder="e.g. Supplier delivery, damage, recount"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </div>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button onClick={() => setModalMode(null)} className="px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:bg-slate-800">Cancel</button>
              <button
                onClick={() => {
                  if (stockAdjustment.quantity_change === 0) {
                    toast.warning('Enter a non-zero quantity to adjust stock');
                    return;
                  }
                  adjustStockMutation.mutate({
                    product_id: selectedProduct.id,
                    quantity_change: stockAdjustment.quantity_change,
                    reason: stockAdjustment.movement_type || 'MANUAL_ADJUSTMENT',
                    note: stockAdjustment.note || null,
                  });
                }}
                disabled={adjustStockMutation.isPending || stockAdjustment.quantity_change === 0}
                className="px-4 py-1.5 rounded-lg text-xs font-bold bg-emerald-500 text-slate-950 hover:bg-emerald-400 shadow-md shadow-emerald-500/20 disabled:opacity-50"
              >
                Confirm Adjustment
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
