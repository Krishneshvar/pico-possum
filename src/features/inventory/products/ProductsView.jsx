import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Package,
  Plus,
  Search,
  Edit2,
  Trash2,
  ArrowUpDown,
  X,
  AlertCircle,
  Check
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise, paiseToRupees, rupeesToPaise } from '../../../lib/formatters';

export default function ProductsView() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [selectedCat, setSelectedCat] = useState('');
  const [modalMode, setModalMode] = useState(null); // 'create' | 'edit' | 'stock' | null
  const [selectedProduct, setSelectedProduct] = useState(null);

  // Form states
  const [formData, setFormData] = useState({
    name: '',
    sku: '',
    category_id: '',
    mrp_rupees: '',
    cost_price_rupees: '',
    tax_rate: '0',
    initial_stock: '0',
    status: 'active',
  });

  const [stockAdjustment, setStockAdjustment] = useState({
    quantity_change: 0,
    note: '',
  });

  // Queries
  const { data: products = [], isLoading } = useQuery({
    queryKey: ['products', selectedCat, search],
    queryFn: () => tauriApi.getProducts(selectedCat || null, search || null),
  });

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: () => tauriApi.getCategories(),
  });

  // Mutations
  const createMutation = useMutation({
    mutationFn: (payload) => tauriApi.createProduct(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      setModalMode(null);
    },
    onError: (err) => alert(`Error: ${err}`),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => tauriApi.updateProduct(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      setModalMode(null);
    },
    onError: (err) => alert(`Error: ${err}`),
  });

  const deleteMutation = useMutation({
    mutationFn: (id) => tauriApi.deleteProduct(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['products'] }),
    onError: (err) => alert(`Error: ${err}`),
  });

  const adjustStockMutation = useMutation({
    mutationFn: (payload) => tauriApi.adjustStock(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      setModalMode(null);
    },
    onError: (err) => alert(`Error: ${err}`),
  });

  const handleOpenCreate = () => {
    setFormData({
      name: '',
      sku: '',
      category_id: selectedCat || '',
      mrp_rupees: '',
      cost_price_rupees: '',
      tax_rate: '0',
      initial_stock: '0',
      status: 'active',
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
      status: p.status,
    });
    setModalMode('edit');
  };

  const handleOpenStock = (p) => {
    setSelectedProduct(p);
    setStockAdjustment({ quantity_change: 0, note: 'Restock / Inventory adjustment' });
    setModalMode('stock');
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.name.trim()) {
      alert('Product name is required');
      return;
    }

    if (modalMode === 'create') {
      createMutation.mutate({
        name: formData.name.trim(),
        sku: formData.sku.trim() || null,
        category_id: formData.category_id || null,
        mrp_paise: rupeesToPaise(formData.mrp_rupees),
        cost_price_paise: rupeesToPaise(formData.cost_price_rupees),
        tax_rate: parseFloat(formData.tax_rate) || 0,
        initial_stock: parseInt(formData.initial_stock, 10) || 0,
        status: formData.status,
      });
    } else if (modalMode === 'edit') {
      updateMutation.mutate({
        id: selectedProduct.id,
        payload: {
          name: formData.name.trim(),
          sku: formData.sku.trim() || null,
          category_id: formData.category_id || null,
          mrp_paise: rupeesToPaise(formData.mrp_rupees),
          cost_price_paise: rupeesToPaise(formData.cost_price_rupees),
          tax_rate: parseFloat(formData.tax_rate) || 0,
          status: formData.status,
        },
      });
    }
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Top Header */}
      <div className="h-16 px-6 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <Package className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-100">Product Catalog</h2>
            <p className="text-xs text-slate-400">Manage catalog, pricing, tax rates, and stock</p>
          </div>
        </div>

        <button
          onClick={handleOpenCreate}
          className="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-emerald-500/20 transition-all"
        >
          <Plus className="w-4 h-4" />
          <span>Add Product</span>
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="p-4 border-b border-slate-800/80 bg-slate-900/30 flex items-center gap-4">
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            type="text"
            placeholder="Search by product name or SKU..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-9 pr-4 py-2 rounded-xl bg-slate-900 border border-slate-800 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-emerald-500"
          />
        </div>

        <select
          value={selectedCat}
          onChange={(e) => setSelectedCat(e.target.value)}
          className="bg-slate-900 border border-slate-800 text-xs text-slate-300 rounded-xl px-3 py-2 focus:outline-none focus:border-emerald-500"
        >
          <option value="">All Categories</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>

      {/* Products Table */}
      <div className="flex-1 overflow-y-auto">
        <table className="w-full text-left border-collapse">
          <thead className="bg-slate-900/80 text-[11px] font-semibold text-slate-400 uppercase tracking-wider sticky top-0 border-b border-slate-800 z-10">
            <tr>
              <th className="py-3 px-4">SKU</th>
              <th className="py-3 px-4">Name</th>
              <th className="py-3 px-4">Category</th>
              <th className="py-3 px-4 text-right">MRP</th>
              <th className="py-3 px-4 text-right">Cost Price</th>
              <th className="py-3 px-4 text-center">Tax %</th>
              <th className="py-3 px-4 text-center">Stock</th>
              <th className="py-3 px-4 text-center">Status</th>
              <th className="py-3 px-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/50 text-xs">
            {products.length === 0 ? (
              <tr>
                <td colSpan="9" className="py-12 text-center text-slate-500">
                  No products found.
                </td>
              </tr>
            ) : (
              products.map((p) => {
                const stock = p.current_stock;
                const isOutOfStock = stock <= 0;
                const isLowStock = stock > 0 && stock <= 5;

                return (
                  <tr key={p.id} className="hover:bg-slate-900/40 transition-colors">
                    <td className="py-3 px-4 font-mono text-slate-400">{p.sku || '-'}</td>
                    <td className="py-3 px-4 font-semibold text-slate-200">{p.name}</td>
                    <td className="py-3 px-4 text-slate-400">{p.category_name || '-'}</td>
                    <td className="py-3 px-4 text-right font-mono font-bold text-slate-100">
                      {formatPaise(p.mrp_paise)}
                    </td>
                    <td className="py-3 px-4 text-right font-mono text-slate-400">
                      {formatPaise(p.cost_price_paise)}
                    </td>
                    <td className="py-3 px-4 text-center font-mono text-slate-400">
                      {p.tax_rate}%
                    </td>
                    <td className="py-3 px-4 text-center">
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-bold font-mono ${
                          isOutOfStock
                            ? 'bg-rose-500/15 text-rose-400 border border-rose-500/30'
                            : isLowStock
                            ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
                            : 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                        }`}
                      >
                        {stock}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-center">
                      <span className="text-[11px] capitalize text-slate-400">{p.status}</span>
                    </td>
                    <td className="py-3 px-4 text-right space-x-1">
                      <button
                        onClick={() => handleOpenStock(p)}
                        className="px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-[11px] font-medium"
                        title="Adjust Stock"
                      >
                        Stock
                      </button>
                      <button
                        onClick={() => handleOpenEdit(p)}
                        className="p-1 rounded text-slate-400 hover:text-emerald-400 hover:bg-slate-800"
                        title="Edit Product"
                      >
                        <Edit2 className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => {
                          if (confirm(`Are you sure you want to delete ${p.name}?`)) {
                            deleteMutation.mutate(p.id);
                          }
                        }}
                        className="p-1 rounded text-slate-400 hover:text-rose-400 hover:bg-slate-800"
                        title="Delete Product"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* ── Product Create / Edit Modal ──────────────────────────────── */}
      {(modalMode === 'create' || modalMode === 'edit') && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 shadow-2xl space-y-4">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <h3 className="font-bold text-sm text-slate-100">
                {modalMode === 'create' ? 'Add New Product' : 'Edit Product'}
              </h3>
              <button onClick={() => setModalMode(null)} className="text-slate-400 hover:text-white">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 block mb-1">Product Name *</label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="e.g. Parle-G Biscuit 250g"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-slate-400 block mb-1">SKU / Barcode</label>
                  <input
                    type="text"
                    value={formData.sku}
                    onChange={(e) => setFormData({ ...formData, sku: e.target.value })}
                    placeholder="e.g. 890123456789"
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono"
                  />
                </div>
                <div>
                  <label className="text-slate-400 block mb-1">Category</label>
                  <select
                    value={formData.category_id}
                    onChange={(e) => setFormData({ ...formData, category_id: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                  >
                    <option value="">No Category</option>
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-slate-400 block mb-1">MRP (₹) *</label>
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
                  <label className="text-slate-400 block mb-1">Cost Price (₹)</label>
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
                {modalMode === 'create' && (
                  <div>
                    <label className="text-slate-400 block mb-1">Initial Stock</label>
                    <input
                      type="number"
                      value={formData.initial_stock}
                      onChange={(e) => setFormData({ ...formData, initial_stock: e.target.value })}
                      placeholder="0"
                      className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono"
                    />
                  </div>
                )}
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setModalMode(null)}
                  className="px-4 py-2 rounded-lg font-semibold text-slate-400 hover:bg-slate-800"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  className="px-5 py-2 rounded-lg font-bold bg-emerald-500 text-slate-950 hover:bg-emerald-400 transition-colors shadow-md shadow-emerald-500/20"
                >
                  {modalMode === 'create' ? 'Create Product' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Stock Adjustment Modal ───────────────────────────────────── */}
      {modalMode === 'stock' && selectedProduct && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-2xl">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <div>
                <h3 className="font-bold text-sm text-slate-100">Adjust Stock</h3>
                <p className="text-[11px] text-slate-400 truncate max-w-[200px]">{selectedProduct.name}</p>
              </div>
              <button onClick={() => setModalMode(null)} className="text-slate-400 hover:text-white">
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="bg-slate-950 p-3 rounded-lg border border-slate-800 text-xs flex justify-between">
              <span className="text-slate-400">Current Stock:</span>
              <strong className="text-slate-100 font-mono text-sm">{selectedProduct.current_stock}</strong>
            </div>

            <div className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 block mb-1">Quantity Change (+ to add, - to deduct)</label>
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
                  placeholder="e.g. Supplier delivery, damage, inventory check"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setModalMode(null)}
                className="px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:bg-slate-800"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  if (stockAdjustment.quantity_change === 0) return;
                  adjustStockMutation.mutate({
                    product_id: selectedProduct.id,
                    quantity_change: stockAdjustment.quantity_change,
                    reason: 'MANUAL_ADJUSTMENT',
                    note: stockAdjustment.note || null,
                  });
                }}
                className="px-4 py-1.5 rounded-lg text-xs font-bold bg-emerald-500 text-slate-950 hover:bg-emerald-400 shadow-md shadow-emerald-500/20"
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
