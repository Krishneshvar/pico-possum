import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  History,
  Receipt,
  User,
  ArrowDownRight,
  ArrowUpRight,
  Package,
  Calendar,
  RotateCcw,
  RefreshCw,
  ShoppingBag,
  TrendingUp,
  Tag,
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise, formatDateTime } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';

export default function ProductFlowView() {
  const todayStr = new Date().toISOString().split('T')[0];
  const firstOfMonthStr = new Date(new Date().getFullYear(), new Date().getMonth(), 1)
    .toISOString()
    .split('T')[0];

  const [selectedProductId, setSelectedProductId] = useState('');
  const [fromDate, setFromDate] = useState(firstOfMonthStr);
  const [toDate, setToDate] = useState(todayStr);

  const { data: products = [] } = useQuery({
    queryKey: ['products'],
    queryFn: () => tauriApi.getProducts(null, null),
  });

  const selectedProduct = useMemo(
    () => products.find((p) => p.id === selectedProductId) || null,
    [products, selectedProductId]
  );

  const hasSelectedProduct = Boolean(selectedProductId);

  const { data: flowItems = [], isLoading, refetch } = useQuery({
    queryKey: ['product_flow', selectedProductId, fromDate, toDate],
    queryFn: () =>
      tauriApi.getProductFlow(selectedProductId || null, fromDate || null, toDate || null, 500),
    enabled: hasSelectedProduct,
  });

  // Calculate Product Summary Metrics
  const summaryMetrics = useMemo(() => {
    if (!flowItems.length) {
      return {
        unitsSold: 0,
        totalRevenue: 0,
        avgPrice: selectedProduct?.unit_price_paise || 0,
      };
    }
    const salesOnly = flowItems.filter((f) => f.quantity < 0 || f.event_type === 'SALE');
    const itemsToCount = salesOnly.length > 0 ? salesOnly : flowItems;
    const unitsSold = itemsToCount.reduce((acc, r) => acc + Math.abs(r.quantity || 0), 0);
    const totalRevenue = itemsToCount.reduce((acc, r) => acc + (r.total_paise || 0), 0);
    const avgPrice = unitsSold > 0 ? Math.round(totalRevenue / unitsSold) : 0;

    return {
      unitsSold,
      totalRevenue,
      avgPrice,
    };
  }, [flowItems, selectedProduct]);

  const columns = [
    {
      key: 'created_at',
      label: 'Date & Time',
      className: 'text-content-secondary text-xs whitespace-nowrap',
      render: (r) => formatDateTime(r.created_at),
    },
    {
      key: 'invoice_number',
      label: 'Invoice / Bill #',
      className: 'font-mono text-content font-medium',
      render: (r) => (
        <span className="inline-flex items-center gap-1.5 text-content">
          <Receipt className="w-3.5 h-3.5 text-content-muted" />
          <span>{r.invoice_number || '—'}</span>
        </span>
      ),
    },
    {
      key: 'customer_name',
      label: 'Customer',
      className: 'text-content-secondary',
      render: (r) => (
        <span className="inline-flex items-center gap-1.5 text-content-secondary">
          <User className="w-3 h-3 text-content-muted shrink-0" />
          <span className="truncate max-w-[160px]">{r.customer_name || 'Walk-in Customer'}</span>
        </span>
      ),
    },
    {
      key: 'event_type',
      label: 'Event Type',
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => (
        <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-surface-secondary text-content-secondary border border-border">
          {r.event_type}
        </span>
      ),
    },
    {
      key: 'quantity',
      label: 'Qty Change',
      headerClassName: 'text-center',
      className: 'text-center font-medium financial-value',
      render: (r) => {
        const isOut = r.quantity < 0;
        return (
          <span
            className={`inline-flex items-center gap-1 font-medium ${
              isOut ? 'text-danger' : 'text-success'
            }`}
          >
            {isOut ? <ArrowDownRight className="w-3.5 h-3.5" /> : <ArrowUpRight className="w-3.5 h-3.5" />}
            <span>{Math.abs(r.quantity)}</span>
          </span>
        );
      },
    },
    {
      key: 'unit_price_paise',
      label: 'Unit Price',
      headerClassName: 'text-right',
      className: 'text-right text-content-secondary financial-value',
      render: (r) => formatPaise(r.unit_price_paise),
    },
    {
      key: 'total_paise',
      label: 'Revenue Total',
      headerClassName: 'text-right',
      className: 'text-right font-medium text-content financial-value',
      render: (r) => formatPaise(r.total_paise),
    },
    {
      key: 'payment_method',
      label: 'Payment',
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => (
        <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-surface-secondary text-content-secondary border border-border">
          {r.payment_method || '—'}
        </span>
      ),
    },
  ];

  const handleReset = () => {
    setSelectedProductId('');
    setFromDate(firstOfMonthStr);
    setToDate(todayStr);
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas text-content overflow-hidden">
      {/* Header */}
      <div className="h-14 px-6 border-b border-border bg-surface flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-brand-subtle text-brand flex items-center justify-center border border-brand/20">
            <History className="w-4 h-4" />
          </div>
          <div>
            <h1 className="text-sm font-semibold text-content">Product Sales History</h1>
            <p className="text-xs text-content-secondary">
              Track historical checkout velocity, price movement, and inventory sales flow
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {(selectedProductId || fromDate !== firstOfMonthStr || toDate !== todayStr) && (
            <button
              onClick={handleReset}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface border border-border hover:bg-surface-secondary text-content-secondary hover:text-content text-xs font-medium transition-colors"
            >
              <RotateCcw className="w-3.5 h-3.5 text-content-muted" />
              <span>Reset</span>
            </button>
          )}
          <button
            onClick={() => refetch()}
            disabled={!hasSelectedProduct}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface border border-border hover:bg-surface-secondary text-content-secondary hover:text-content text-xs font-medium transition-colors disabled:opacity-40 disabled:pointer-events-none"
            title="Refresh History"
          >
            <RefreshCw className="w-3.5 h-3.5 text-brand" />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Control Filter Bar */}
      <div className="px-6 py-3.5 border-b border-border bg-surface flex items-center gap-4 flex-wrap text-xs">
        {/* Product Picker */}
        <div className="flex items-center gap-2 min-w-64 flex-1 max-w-md">
          <span className="text-content-secondary font-medium shrink-0">Select Product:</span>
          <select
            value={selectedProductId}
            onChange={(e) => setSelectedProductId(e.target.value)}
            className="w-full bg-surface-secondary border border-border-strong text-content rounded-lg px-3 py-1.5 focus:border-brand focus:outline-none cursor-pointer truncate"
          >
            <option value="">-- Choose a product to inspect history --</option>
            {products.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} {p.sku ? `(SKU: ${p.sku})` : ''}
              </option>
            ))}
          </select>
        </div>

        {/* Date Filters */}
        <div className="flex items-center gap-2">
          <Calendar className="w-4 h-4 text-content-muted" />
          <span className="text-content-secondary font-medium">From:</span>
          <input
            type="date"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            className="bg-surface-secondary border border-border-strong text-content rounded-lg px-2.5 py-1 text-xs font-mono focus:border-brand focus:outline-none"
          />
        </div>

        <div className="flex items-center gap-2">
          <span className="text-content-secondary font-medium">To:</span>
          <input
            type="date"
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            className="bg-surface-secondary border border-border-strong text-content rounded-lg px-2.5 py-1 text-xs font-mono focus:border-brand focus:outline-none"
          />
        </div>
      </div>

      {/* Main Content Area */}
      <div className="flex-1 overflow-y-auto flex flex-col">
        {hasSelectedProduct ? (
          <div className="flex-1 flex flex-col p-6 space-y-6">
            {/* Product Summary Card */}
            <div className="p-4 rounded-xl bg-surface border border-border">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <h2 className="text-base font-semibold text-content">
                      {selectedProduct?.name}
                    </h2>
                    {selectedProduct?.sku && (
                      <span className="text-xs font-mono px-2 py-0.5 rounded bg-surface-secondary border border-border text-content-secondary">
                        SKU: {selectedProduct.sku}
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-content-secondary">
                    Transaction audit and historical customer sales records
                  </p>
                </div>

                <div className="flex items-center gap-3 flex-wrap">
                  <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface-secondary border border-border">
                    <ShoppingBag className="w-3.5 h-3.5 text-brand" />
                    <span className="text-xs text-content-secondary">Total Units Sold:</span>
                    <span className="text-xs font-bold text-content financial-value">
                      {summaryMetrics.unitsSold}
                    </span>
                  </div>

                  <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface-secondary border border-border">
                    <span className="text-xs text-content-secondary">Current Stock:</span>
                    <span
                      className={`text-xs font-semibold px-2 py-0.5 rounded-full border financial-value ${
                        (selectedProduct?.stock_quantity ?? selectedProduct?.current_stock ?? 0) <= 5
                          ? 'bg-warning/10 text-warning border-warning/30'
                          : 'bg-success/10 text-success border-success/30'
                      }`}
                    >
                      {selectedProduct?.stock_quantity ?? selectedProduct?.current_stock ?? 0} units available
                    </span>
                  </div>
                </div>
              </div>
            </div>

            {/* Transaction Flow Table */}
            <div className="flex-1 overflow-hidden border border-border rounded-xl bg-surface flex flex-col">
              <div className="px-4 py-3 border-b border-border flex items-center justify-between">
                <h3 className="text-xs font-semibold text-content uppercase tracking-wider">
                  Transaction Records
                </h3>
              </div>
              <div className="flex-1 overflow-hidden">
                <DataTable
                  columns={columns}
                  persistKey="product-flow"
                  data={flowItems}
                  keyFn={(r) => r.id}
                  searchKeys={['invoice_number', 'customer_name', 'payment_method']}
                  pageSize={25}
                  emptyMessage="No transaction events found for this product in the selected period."
                  isLoading={isLoading}
                />
              </div>
            </div>
          </div>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center p-8 text-center">
            <div className="w-12 h-12 rounded-xl bg-surface-secondary border border-border flex items-center justify-center text-content-muted mb-3">
              <Package className="w-6 h-6" />
            </div>
            <h3 className="text-sm font-semibold text-content mb-1">No Product Selected</h3>
            <p className="text-xs text-content-secondary max-w-sm">
              Please choose a product from the dropdown above to inspect its transaction history,
              sales velocity, and average selling price.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
