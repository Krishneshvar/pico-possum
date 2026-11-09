import { useState, useEffect, useMemo, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Package,
  Plus,
  Edit2,
  Trash2,
  X,
  ArrowUpDown,
  AlertTriangle,
  CheckCircle2,
  Download,
  FileText,
  FileSpreadsheet,
  ChevronDown,
} from 'lucide-react';
import * as XLSX from 'xlsx';
import DropdownActions from '../../../components/common/DropdownActions';
import ImportCsvButton from '../../../components/common/ImportCsvButton';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import { formatPaise, rupeesToPaise, getCurrencySymbol } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';
import Select from '../../../components/common/Select';

export default function ProductsView() {
  const queryClient = useQueryClient();
  const currencySymbol = getCurrencySymbol();
  const [modalMode, setModalMode] = useState(null); // 'create' | 'edit' | 'stock'
  const [selectedProduct, setSelectedProduct] = useState(null);

  const [formData, setFormData] = useState({
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
  });
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

  // Category counts and options with parentheses
  const categoryCounts = useMemo(() => {
    const counts = {};
    for (const p of products) {
      if (p.category_id) {
        counts[p.category_id] = (counts[p.category_id] || 0) + 1;
      }
    }
    return counts;
  }, [products]);

  const categoryFilterOptions = useMemo(
    () =>
      categories.map((c) => ({
        value: String(c.id),
        label: `${c.name} (${categoryCounts[c.id] || 0})`,
      })),
    [categories, categoryCounts]
  );

  // Stock level counts and options with parentheses
  const stockCounts = useMemo(() => {
    let inStock = 0;
    let lowStock = 0;
    let outOfStock = 0;
    for (const p of products) {
      const stock = p.current_stock || 0;
      const alertThreshold = p.min_stock_alert ?? 5;
      if (stock <= 0) outOfStock++;
      else if (stock <= alertThreshold) lowStock++;
      else inStock++;
    }
    return { inStock, lowStock, outOfStock };
  }, [products]);

  const stockFilterOptions = useMemo(
    () => [
      { value: 'in_stock', label: `In stock (${stockCounts.inStock})` },
      { value: 'low_stock', label: `Low stock (${stockCounts.lowStock})` },
      { value: 'out_of_stock', label: `Out of stock (${stockCounts.outOfStock})` },
    ],
    [stockCounts]
  );

  // Status counts and options with parentheses
  const statusCounts = useMemo(() => {
    const counts = { active: 0, inactive: 0, discontinued: 0 };
    for (const p of products) {
      const s = p.status || 'active';
      counts[s] = (counts[s] || 0) + 1;
    }
    return counts;
  }, [products]);

  const statusFilterOptions = useMemo(
    () => [
      { value: 'active', label: `Active (${statusCounts.active || 0})` },
      { value: 'inactive', label: `Inactive (${statusCounts.inactive || 0})` },
      { value: 'discontinued', label: `Discontinued (${statusCounts.discontinued || 0})` },
    ],
    [statusCounts]
  );

  const createMutation = useMutation({
    mutationFn: (p) => tauriApi.createProduct(p),
    onSuccess: (_data, product) => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['next_sku'] });
      setModalMode(null);
      toast.success(`Product "${product.name}" added to catalog`);
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
      toast.success('Product removed from active catalog');
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
      min_stock_alert: (p.min_stock_alert != null ? p.min_stock_alert : 5).toString(),
      uom: 'Piece',
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

  const handleDelete = async (product) => {
    const { confirmed } = await confirmDialog({
      title: `Delete "${product.name}"?`,
      message: `Are you sure you want to delete "${product.name}" (SKU: ${product.sku || 'N/A'})? This item will be removed from the active catalog.`,
      confirmLabel: 'Delete Product',
      cancelLabel: 'Cancel',
      tone: 'danger',
    });
    if (confirmed) {
      deleteMutation.mutate(product.id);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.name.trim()) {
      toast.warning('Product name is required');
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
    if (modalMode === 'create') {
      createMutation.mutate({ ...payload, initial_stock: parseInt(formData.initial_stock, 10) || 0 });
    } else {
      updateMutation.mutate({ id: selectedProduct.id, payload });
    }
  };

  const [exportMenuOpen, setExportMenuOpen] = useState(false);
  const exportMenuRef = useRef(null);

  useEffect(() => {
    if (!exportMenuOpen) return;
    const handleClickOutside = (e) => {
      if (exportMenuRef.current && !exportMenuRef.current.contains(e.target)) {
        setExportMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [exportMenuOpen]);

  const escapeCsv = (val) => {
    if (val === null || val === undefined) return '""';
    const str = String(val).replace(/"/g, '""');
    return `"${str}"`;
  };

  const handleExportCsv = () => {
    if (!products.length) {
      toast.warning('No products to export');
      return;
    }

    const headers = [
      'Product Name',
      'SKU',
      'Category',
      'Cost Price (Rs)',
      'MRP (Rs)',
      'GST (%)',
      'Current Stock',
      'Min Stock Alert',
      'Unit',
      'Status',
    ];

    const rows = products.map((p) => [
      escapeCsv(p.name || ''),
      escapeCsv(p.sku || ''),
      escapeCsv(p.category_name || 'Uncategorized'),
      escapeCsv((p.cost_price_paise / 100).toFixed(2)),
      escapeCsv((p.mrp_paise / 100).toFixed(2)),
      escapeCsv(p.tax_rate ?? 0),
      escapeCsv(p.current_stock ?? 0),
      escapeCsv(p.min_stock_alert ?? 5),
      escapeCsv(p.uom || 'Piece'),
      escapeCsv(p.status || 'active'),
    ]);

    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    const dateStr = new Date().toISOString().split('T')[0];
    link.setAttribute('download', `products_${dateStr}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    toast.success(`Exported ${products.length} products to CSV`);
  };

  const handleExportXlsx = () => {
    if (!products.length) {
      toast.warning('No products to export');
      return;
    }

    const headers = [
      'Product Name',
      'SKU',
      'Category',
      'Cost Price (Rs)',
      'MRP (Rs)',
      'GST (%)',
      'Current Stock',
      'Min Stock Alert',
      'Unit',
      'Status',
    ];

    const rows = products.map((p) => [
      p.name || '',
      p.sku || '',
      p.category_name || 'Uncategorized',
      parseFloat((p.cost_price_paise / 100).toFixed(2)),
      parseFloat((p.mrp_paise / 100).toFixed(2)),
      p.tax_rate ?? 0,
      p.current_stock ?? 0,
      p.min_stock_alert ?? 5,
      p.uom || 'Piece',
      p.status || 'active',
    ]);

    const ws = XLSX.utils.aoa_to_sheet([headers, ...rows]);
    ws['!cols'] = [
      { wch: 25 },
      { wch: 16 },
      { wch: 18 },
      { wch: 14 },
      { wch: 16 },
      { wch: 10 },
      { wch: 14 },
      { wch: 14 },
      { wch: 10 },
      { wch: 12 },
    ];

    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Products');
    const dateStr = new Date().toISOString().split('T')[0];
    XLSX.writeFile(wb, `products_${dateStr}.xlsx`);
    toast.success(`Exported ${products.length} products to Excel`);
  };

  const columns = [
    {
      key: 'name',
      label: 'Product',
      className: 'font-semibold text-text-primary text-xs',
      render: (r) => (
        <div>
          <span>{r.name}</span>
          {r.category_name && (
            <div className="text-[10px] text-text-muted">{r.category_name}</div>
          )}
        </div>
      ),
    },
    {
      key: 'sku',
      label: 'SKU / Barcode',
      className: 'font-mono text-text-muted text-xs',
      render: (r) => r.sku || '—',
    },
    {
      key: 'cost_price_paise',
      label: 'Cost Price',
      headerClassName: 'text-right',
      className: 'text-right financial-value text-text-muted text-xs',
      render: (r) => formatPaise(r.cost_price_paise),
    },
    {
      key: 'mrp_paise',
      label: 'MRP',
      headerClassName: 'text-right',
      className: 'text-right financial-value font-bold text-text-primary text-xs',
      render: (r) => formatPaise(r.mrp_paise),
    },
    {
      key: 'tax_rate',
      label: 'GST %',
      headerClassName: 'text-center',
      className: 'text-center font-mono text-text-muted text-xs',
      render: (r) => `${r.tax_rate}%`,
    },
    {
      key: 'current_stock',
      label: 'Stock Level',
      headerClassName: 'text-center',
      className: 'text-center text-xs',
      render: (r) => {
        const s = r.current_stock;
        const alertThreshold = r.min_stock_alert ?? 5;

        if (allowNegativeStock) {
          return (
            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-bold font-mono bg-surface-secondary text-text-secondary border border-border-default">
              {s}
            </span>
          );
        }

        const isOut = s <= 0;
        const isLow = s <= alertThreshold && !isOut;
        const cls = isOut
          ? 'bg-danger-bg text-danger border-danger/30'
          : isLow
          ? 'bg-warning-bg text-warning border-warning/30'
          : 'bg-success-bg text-success border-success/30';
        return (
          <span
            title={isLow ? `Low stock alert (≤ ${alertThreshold})` : isOut ? 'Out of stock' : `Stock: ${s}`}
            className={`inline-flex items-center justify-center px-2.5 py-0.5 rounded-full text-[11px] font-bold font-mono border ${cls}`}
          >
            {s}
          </span>
        );
      },
    },
    {
      key: 'status',
      label: 'Status',
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => {
        const status = r.status || 'active';
        const styles = {
          active: 'bg-success-bg text-success border-success/30',
          inactive: 'bg-surface-secondary text-text-muted border-border-default',
          discontinued: 'bg-danger-bg text-danger border-danger/30',
        };
        return (
          <span
            className={`inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-semibold uppercase tracking-wider border ${
              styles[status] || styles.active
            }`}
          >
            {status}
          </span>
        );
      },
    },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas overflow-hidden">
      {/* ── Page Header ── */}
      <div className="h-14 px-5 border-b border-border-default bg-surface flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-brand-subtle text-brand flex items-center justify-center">
            <Package className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-text-primary">Product Catalog</h2>
            <p className="text-[11px] text-text-muted">Manage items, rates, categories, and inventory</p>
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

          {/* Export Dropdown Button */}
          <div ref={exportMenuRef} className="relative">
            <button
              type="button"
              onClick={() => setExportMenuOpen((prev) => !prev)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-border-default bg-surface hover:bg-surface-secondary text-text-secondary hover:text-text-primary transition-colors shadow-2xs cursor-pointer"
              title="Export products catalog as CSV or Excel"
              aria-haspopup="true"
              aria-expanded={exportMenuOpen}
            >
              <Download className="w-3.5 h-3.5 text-text-muted" />
              <span>Export</span>
              <ChevronDown
                className={`w-3 h-3 text-text-muted transition-transform ${exportMenuOpen ? 'rotate-180' : ''}`}
              />
            </button>

            {exportMenuOpen && (
              <div className="absolute right-0 top-full mt-1.5 z-50 bg-surface border border-border-default rounded-xl shadow-xl p-1.5 min-w-[170px] space-y-0.5 text-text-primary animate-scale-in">
                <button
                  type="button"
                  onClick={() => {
                    setExportMenuOpen(false);
                    handleExportCsv();
                  }}
                  className="w-full flex items-center gap-2 px-2.5 py-2 rounded-lg text-xs font-medium text-text-secondary hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
                >
                  <FileText className="w-4 h-4 text-blue-500" />
                  <span>Export as CSV</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setExportMenuOpen(false);
                    handleExportXlsx();
                  }}
                  className="w-full flex items-center gap-2 px-2.5 py-2 rounded-lg text-xs font-medium text-text-secondary hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
                >
                  <FileSpreadsheet className="w-4 h-4 text-emerald-500" />
                  <span>Export as Excel</span>
                </button>
              </div>
            )}
          </div>

          <button
            type="button"
            onClick={handleOpenCreate}
            className="px-3.5 py-1.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-bold text-xs flex items-center gap-1.5 shadow-xs transition-colors cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />
            Add Product
          </button>
        </div>
      </div>

      {/* ── DataTable ── */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          persistKey="products"
          data={products}
          keyFn={(r) => r.id}
          searchKeys={['name', 'sku', 'category_name']}
          filters={[
            {
              type: 'multiselect',
              key: 'category_id',
              label: 'Categories',
              options: categoryFilterOptions,
            },
            {
              type: 'multiselect',
              key: 'stock_level',
              label: 'Stock Level',
              options: stockFilterOptions,
              filterFn: (row, vals) => {
                const stock = row.current_stock || 0;
                const alertThreshold = row.min_stock_alert ?? 5;
                let level = 'in_stock';
                if (stock <= 0) level = 'out_of_stock';
                else if (stock <= alertThreshold) level = 'low_stock';
                return vals.includes(level);
              },
            },
            {
              type: 'multiselect',
              key: 'status',
              label: 'Status',
              options: statusFilterOptions,
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
          emptyMessage="No products found in catalog."
          actions={(row) => (
            <DropdownActions
              items={[
                {
                  label: 'Adjust Stock',
                  icon: <ArrowUpDown className="w-3.5 h-3.5" />,
                  onClick: () => handleOpenStock(row),
                },
                {
                  label: 'Edit Product',
                  icon: <Edit2 className="w-3.5 h-3.5" />,
                  onClick: () => handleOpenEdit(row),
                },
                'divider',
                {
                  label: 'Delete Product',
                  icon: <Trash2 className="w-3.5 h-3.5" />,
                  danger: true,
                  onClick: () => handleDelete(row),
                },
              ]}
            />
          )}
        />
      </div>

      {/* ── Grouped Product Form Modal ── */}
      {(modalMode === 'create' || modalMode === 'edit') && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-surface border border-border-default rounded-2xl max-w-lg w-full p-6 shadow-xl space-y-4 text-text-primary animate-scale-in">
            <div className="flex justify-between items-center border-b border-border-default pb-3">
              <h3 className="font-bold text-sm text-text-primary">
                {modalMode === 'create' ? 'Add New Product' : 'Edit Product'}
              </h3>
              <button
                type="button"
                onClick={() => setModalMode(null)}
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
                    <label className="text-text-secondary block font-semibold mb-1">Category</label>
                    <Select
                      value={formData.category_id}
                      onChange={(val) => setFormData({ ...formData, category_id: val })}
                      placeholder="No Category"
                      options={[
                        { value: '', label: 'No Category' },
                        ...categories.map((c) => ({ value: String(c.id), label: c.name })),
                      ]}
                    />
                  </div>
                </div>
              </div>

              {/* Group 2: Pricing and Tax */}
              <div className="space-y-2.5 pt-2 border-t border-border-default">
                <span className="text-[11px] font-bold text-text-muted uppercase tracking-wider block">
                  Pricing & Taxation
                </span>
                <div className="grid grid-cols-3 gap-3">
                  <div>
                    <label className="text-text-secondary block font-semibold mb-1">Cost Price ({currencySymbol})</label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.cost_price_rupees}
                      onChange={(e) => setFormData({ ...formData, cost_price_rupees: e.target.value })}
                      placeholder="0.00"
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary font-mono focus:border-brand focus:outline-none"
                    />
                  </div>
                  <div>
                    <label className="text-text-secondary block font-semibold mb-1">
                      MRP ({currencySymbol}) <span className="text-danger">*</span>
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      required
                      value={formData.mrp_rupees}
                      onChange={(e) => setFormData({ ...formData, mrp_rupees: e.target.value })}
                      placeholder="100.00"
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary font-mono font-semibold focus:border-brand focus:outline-none"
                    />
                  </div>
                  <div>
                    <label className="text-text-secondary block font-semibold mb-1">GST Tax Rate (%)</label>
                    <input
                      type="number"
                      step="0.1"
                      value={formData.tax_rate}
                      onChange={(e) => setFormData({ ...formData, tax_rate: e.target.value })}
                      placeholder="0"
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary font-mono focus:border-brand focus:outline-none"
                    />
                  </div>
                </div>
              </div>

              {/* Group 3: Inventory Configuration */}
              <div className="space-y-2.5 pt-2 border-t border-border-default">
                <span className="text-[11px] font-bold text-text-muted uppercase tracking-wider block">
                  Inventory Configuration
                </span>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-text-secondary block font-semibold mb-1">Low-Stock Alert Threshold</label>
                    <input
                      type="number"
                      min="0"
                      value={formData.min_stock_alert}
                      onChange={(e) => setFormData({ ...formData, min_stock_alert: e.target.value })}
                      disabled={allowNegativeStock}
                      placeholder="5"
                      className={`w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary font-mono focus:border-brand focus:outline-none ${
                        allowNegativeStock ? 'opacity-50 cursor-not-allowed' : ''
                      }`}
                    />
                  </div>
                  <div>
                    <label className="text-text-secondary block font-semibold mb-1">Product Status</label>
                    <Select
                      value={formData.status}
                      onChange={(val) => setFormData({ ...formData, status: val })}
                      options={[
                        { value: 'active', label: 'Active (Available for POS)' },
                        { value: 'inactive', label: 'Inactive (Hidden)' },
                        { value: 'discontinued', label: 'Discontinued' },
                      ]}
                    />
                  </div>
                </div>

                {modalMode === 'create' && (
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
                  onClick={() => setModalMode(null)}
                  className="px-3.5 py-1.5 rounded-lg font-semibold text-text-secondary hover:bg-surface-secondary border border-border-default cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  className="px-4 py-1.5 rounded-lg font-bold bg-brand text-white hover:bg-brand-hover shadow-xs disabled:opacity-50 cursor-pointer"
                >
                  {modalMode === 'create' ? 'Create Product' : 'Save'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Stock Adjustment Modal (Dedicated Movement Tracking) ── */}
      {modalMode === 'stock' && selectedProduct && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-surface border border-border-default rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-xl text-text-primary animate-scale-in">
            <div className="flex justify-between items-center border-b border-border-default pb-3">
              <div>
                <h3 className="font-bold text-sm text-text-primary">Adjust Stock</h3>
                <p className="text-[11px] text-text-muted truncate max-w-[200px]">{selectedProduct.name}</p>
              </div>
              <button
                type="button"
                onClick={() => setModalMode(null)}
                className="p-1 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-secondary cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="bg-surface-secondary p-3 rounded-lg border border-border-default text-xs flex justify-between">
              <span className="text-text-muted">Current Balance:</span>
              <strong className="text-text-primary font-mono font-bold text-sm">
                {selectedProduct.current_stock} units
              </strong>
            </div>

            <div className="space-y-3 text-xs">
              <div>
                <label className="text-text-secondary block font-semibold mb-1">Movement Type</label>
                <Select
                  value={stockAdjustment.movement_type}
                  onChange={(val) =>
                    setStockAdjustment({ ...stockAdjustment, movement_type: val })
                  }
                  options={[
                    { value: 'RESTOCK', label: 'Supplier Restock Delivery' },
                    { value: 'PURCHASE', label: 'Wholesale Purchase' },
                    { value: 'MANUAL_ADJUSTMENT', label: 'Manual Stock Correction / Audit' },
                    { value: 'RETURN', label: 'Customer Return / Exchange' },
                  ]}
                />
              </div>

              <div>
                <label className="text-text-secondary block font-semibold mb-1">
                  Quantity Change (+ to add, − to deduct)
                </label>
                <input
                  type="number"
                  value={stockAdjustment.quantity_change}
                  onChange={(e) =>
                    setStockAdjustment({
                      ...stockAdjustment,
                      quantity_change: parseInt(e.target.value, 10) || 0,
                    })
                  }
                  className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none font-mono"
                />
              </div>

              <div>
                <label className="text-text-secondary block font-semibold mb-1">Reason / Note</label>
                <input
                  type="text"
                  value={stockAdjustment.note}
                  onChange={(e) => setStockAdjustment({ ...stockAdjustment, note: e.target.value })}
                  placeholder="e.g. Delivery Challan #882, physical recount"
                  className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-border-default">
              <button
                type="button"
                onClick={() => setModalMode(null)}
                className="px-3 py-1.5 rounded-lg text-xs font-semibold text-text-secondary hover:bg-surface-secondary border border-border-default cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
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
                className="px-4 py-1.5 rounded-lg text-xs font-bold bg-brand text-white hover:bg-brand-hover shadow-xs disabled:opacity-50 cursor-pointer"
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
