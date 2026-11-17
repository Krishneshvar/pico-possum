import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Package,
  Plus,
  Edit2,
  Trash2,
  ArrowUpDown,
} from 'lucide-react';
import DropdownActions from '../../../components/common/DropdownActions';
import ImportCsvButton from '../../../components/common/ImportCsvButton';
import ExportButton from '../../../components/common/ExportButton';
import { tauriApi } from '../../../lib/tauri';
import { strings } from '../../../lib/strings';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import { getCurrencySymbol } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';
import ProductModal from './ProductModal';
import StockAdjustmentModal from './StockAdjustmentModal';
import { exportProductsCsv, exportProductsXlsx } from './productExport';
import { getProductColumns } from './productColumns';

export default function ProductsView() {
  const queryClient = useQueryClient();
  const currencySymbol = getCurrencySymbol();
  const [modalMode, setModalMode] = useState(null); // 'create' | 'edit' | 'stock'
  const [selectedProduct, setSelectedProduct] = useState(null);

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

  const allowNegativeStock = generalSettings?.allow_negative_stock ?? false;

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
        label: strings.common.table.filterCount(c.name, categoryCounts[c.id] || 0),
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
      { value: 'in_stock', label: strings.products.filters.inStock(stockCounts.inStock) },
      { value: 'low_stock', label: strings.products.filters.lowStock(stockCounts.lowStock) },
      {
        value: 'out_of_stock',
        label: strings.products.filters.outOfStock(stockCounts.outOfStock),
      },
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
      { value: 'active', label: strings.products.filters.active(statusCounts.active || 0) },
      { value: 'inactive', label: strings.products.filters.inactive(statusCounts.inactive || 0) },
      {
        value: 'discontinued',
        label: strings.products.filters.discontinued(statusCounts.discontinued || 0),
      },
    ],
    [statusCounts]
  );

  const createMutation = useMutation({
    mutationFn: (p) => tauriApi.createProduct(p),
    onSuccess: (_data, product) => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['next_sku'] });
      setModalMode(null);
      toast.success(strings.products.toasts.created(product.name));
    },
    onError: (err) =>
      toast.error(strings.products.toasts.createFailed(err), {
        title: strings.products.toasts.createFailedTitle,
      }),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => tauriApi.updateProduct(id, payload),
    onSuccess: (_data, { payload }) => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      setModalMode(null);
      toast.success(strings.products.toasts.updated(payload.name));
    },
    onError: (err) =>
      toast.error(strings.products.toasts.updateFailed(err), {
        title: strings.products.toasts.updateFailedTitle,
      }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id) => tauriApi.deleteProduct(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      toast.success(strings.products.toasts.deleted);
    },
    onError: (err) =>
      toast.error(strings.products.toasts.deleteFailed(err), {
        title: strings.products.toasts.deleteFailedTitle,
      }),
  });

  const adjustStockMutation = useMutation({
    mutationFn: (p) => tauriApi.adjustStock(p),
    onSuccess: (_data, adjustment) => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['stock_movements'] });
      setModalMode(null);
      const delta = adjustment.quantity_change;
      toast.success(
        strings.products.toasts.stockAdjusted(
          delta,
          selectedProduct?.name ?? strings.products.stock.fallbackProductName
        )
      );
    },
    onError: (err) =>
      toast.error(strings.products.toasts.stockUpdateFailed(err), {
        title: strings.products.toasts.stockUpdateFailedTitle,
      }),
  });

  const handleOpenCreate = () => {
    setSelectedProduct(null);
    setModalMode('create');
  };

  const handleOpenEdit = (p) => {
    setSelectedProduct(p);
    setModalMode('edit');
  };

  const handleOpenStock = (p) => {
    setSelectedProduct(p);
    setModalMode('stock');
  };

  const handleDelete = async (product) => {
    const { confirmed } = await confirmDialog({
      title: strings.products.dialog.deleteTitle(product.name),
      message: strings.products.dialog.deleteMessage(
        product.name,
        product.sku || strings.common.states.notAvailable
      ),
      confirmLabel: strings.products.dialog.deleteConfirm,
      cancelLabel: strings.common.actions.cancel,
      tone: 'danger',
    });
    if (confirmed) {
      deleteMutation.mutate(product.id);
    }
  };

  const handleProductSubmit = (payload, isCreate) => {
    if (isCreate) {
      createMutation.mutate(payload);
    } else {
      updateMutation.mutate({ id: selectedProduct.id, payload });
    }
  };

  const handleStockSubmit = (adjustment) => {
    adjustStockMutation.mutate(adjustment);
  };

  const handleExportCsv = () => exportProductsCsv({ products, currencySymbol });
  const handleExportXlsx = () => exportProductsXlsx({ products, currencySymbol });

  const columns = useMemo(
    () => getProductColumns({ allowNegativeStock }),
    [allowNegativeStock]
  );

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas overflow-hidden">
      {/* ── Page Header ── */}
      <div className="h-14 px-5 border-b border-border-default bg-surface flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-brand-subtle text-brand flex items-center justify-center">
            <Package className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-text-primary">{strings.products.view.title}</h2>
            <p className="text-[11px] text-text-muted">{strings.products.view.subtitle}</p>
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

          <ExportButton
            onExportCsv={handleExportCsv}
            onExportXlsx={handleExportXlsx}
          />

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
      <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
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
              transform: (v) => (v != null ? Number(v) / 100 : 0),
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

      {/* ── Product Create / Edit Modal ── */}
      <ProductModal
        open={modalMode === 'create' || modalMode === 'edit'}
        product={modalMode === 'edit' ? selectedProduct : null}
        categories={categories}
        generalSettings={generalSettings}
        onClose={() => setModalMode(null)}
        onSubmit={handleProductSubmit}
        isPending={createMutation.isPending || updateMutation.isPending}
      />

      {/* ── Stock Adjustment Modal ── */}
      <StockAdjustmentModal
        product={modalMode === 'stock' ? selectedProduct : null}
        onClose={() => setModalMode(null)}
        onSubmit={handleStockSubmit}
        isPending={adjustStockMutation.isPending}
      />
    </div>
  );
}
