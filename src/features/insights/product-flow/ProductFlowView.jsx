import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowDownRight, ArrowUpRight, Search, RefreshCw, Layers, Tag, User, Receipt } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise, formatDateTime } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';

export default function ProductFlowView() {
  const todayStr = new Date().toISOString().split('T')[0];
  const firstOfMonthStr = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().split('T')[0];

  const [selectedProductId, setSelectedProductId] = useState('');
  const [fromDate, setFromDate] = useState(firstOfMonthStr);
  const [toDate, setToDate] = useState(todayStr);

  const { data: products = [] } = useQuery({
    queryKey: ['products'],
    queryFn: () => tauriApi.getProducts(null, null),
  });

  const { data: flowItems = [], isLoading, refetch } = useQuery({
    queryKey: ['product_flow', selectedProductId, fromDate, toDate],
    queryFn: () => tauriApi.getProductFlow(selectedProductId || null, fromDate || null, toDate || null, 500),
  });

  const selectedProduct = useMemo(() => {
    return products.find((p) => p.id === selectedProductId) || null;
  }, [products, selectedProductId]);

  // Aggregate stats for current view
  const stats = useMemo(() => {
    let unitsSold = 0;
    let revenuePaise = 0;
    for (const item of flowItems) {
      if (item.quantity < 0) {
        unitsSold += Math.abs(item.quantity);
        revenuePaise += item.total_paise;
      }
    }
    return {
      unitsSold,
      revenuePaise,
      currentStock: selectedProduct ? selectedProduct.current_stock : null,
      avgPrice: unitsSold > 0 ? Math.round(revenuePaise / unitsSold) : 0,
    };
  }, [flowItems, selectedProduct]);

  const columns = [
    {
      key: 'created_at',
      label: 'Date & Time',
      className: 'font-mono text-slate-400 text-[11px] whitespace-nowrap',
      render: (r) => formatDateTime(r.created_at),
    },
    {
      key: 'invoice_number',
      label: 'Bill / Invoice',
      className: 'font-mono text-slate-200 font-semibold',
      render: (r) => (
        <span className="inline-flex items-center gap-1 text-slate-300">
          <Receipt className="w-3 h-3 text-slate-500" />
          {r.invoice_number || '—'}
        </span>
      ),
    },
    {
      key: 'product_name',
      label: 'Product',
      className: 'font-medium text-slate-200',
      render: (r) => (
        <div>
          <span>{r.product_name}</span>
          {r.product_sku && (
            <span className="ml-2 text-[10px] font-mono text-slate-500 px-1 py-0.2 bg-slate-900 rounded border border-slate-800">
              {r.product_sku}
            </span>
          )}
        </div>
      ),
    },
    {
      key: 'customer_name',
      label: 'Customer',
      className: 'text-slate-400',
      render: (r) => (
        <span className="flex items-center gap-1.5">
          <User className="w-3 h-3 text-slate-500 shrink-0" />
          <span className="truncate max-w-[150px]">{r.customer_name || 'Walk-in Customer'}</span>
        </span>
      ),
    },
    {
      key: 'event_type',
      label: 'Event',
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => (
        <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-rose-500/15 text-rose-400 border border-rose-500/20">
          {r.event_type}
        </span>
      ),
    },
    {
      key: 'quantity',
      label: 'Qty Change',
      headerClassName: 'text-center',
      className: 'text-center font-mono font-bold',
      render: (r) => {
        const isOut = r.quantity < 0;
        return (
          <span className={`inline-flex items-center gap-0.5 ${isOut ? 'text-rose-400' : 'text-emerald-400'}`}>
            {isOut ? <ArrowDownRight className="w-3.5 h-3.5" /> : <ArrowUpRight className="w-3.5 h-3.5" />}
            {r.quantity}
          </span>
        );
      },
    },
    {
      key: 'unit_price_paise',
      label: 'Unit Price',
      headerClassName: 'text-right',
      className: 'text-right font-mono text-slate-400',
      render: (r) => formatPaise(r.unit_price_paise),
    },
    {
      key: 'total_paise',
      label: 'Total Value',
      headerClassName: 'text-right',
      className: 'text-right font-mono font-semibold text-slate-200',
      render: (r) => formatPaise(r.total_paise),
    },
    {
      key: 'payment_method',
      label: 'Payment',
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => (
        <span className="text-[10px] font-mono font-semibold px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 border border-slate-700/60">
          {r.payment_method || '—'}
        </span>
      ),
    },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-14 px-5 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <Layers className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-100">Product Flow</h2>
            <p className="text-[11px] text-slate-500">Track item-by-item transactional history and velocity</p>
          </div>
        </div>

        <button
          onClick={() => refetch()}
          className="p-1.5 rounded-lg border border-slate-800 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          title="Refresh Flow Data"
        >
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      {/* Control Filter Bar */}
      <div className="px-5 py-3 border-b border-slate-800/80 bg-slate-900/30 flex items-center gap-3 flex-wrap text-xs">
        {/* Product Picker */}
        <div className="flex items-center gap-1.5 min-w-56 flex-1 max-w-sm">
          <span className="text-slate-400 font-medium">Product:</span>
          <select
            value={selectedProductId}
            onChange={(e) => setSelectedProductId(e.target.value)}
            className="w-full bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2.5 py-1.5 focus:border-emerald-500 focus:outline-none truncate"
          >
            <option value="">All Products</option>
            {products.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} {p.sku ? `(${p.sku})` : ''}
              </option>
            ))}
          </select>
        </div>

        {/* Date Filters */}
        <div className="flex items-center gap-1.5">
          <span className="text-slate-400 font-medium">From:</span>
          <input
            type="date"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            className="bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2 py-1 focus:border-emerald-500 focus:outline-none font-mono"
          />
        </div>

        <div className="flex items-center gap-1.5">
          <span className="text-slate-400 font-medium">To:</span>
          <input
            type="date"
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            className="bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2 py-1 focus:border-emerald-500 focus:outline-none font-mono"
          />
        </div>

        {(selectedProductId || fromDate !== firstOfMonthStr || toDate !== todayStr) && (
          <button
            onClick={() => {
              setSelectedProductId('');
              setFromDate(firstOfMonthStr);
              setToDate(todayStr);
            }}
            className="text-slate-400 hover:text-white px-2.5 py-1 rounded-lg hover:bg-slate-800 transition-colors ml-auto"
          >
            Reset
          </button>
        )}
      </div>

      {/* Product Flow Summary Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-3 bg-slate-900/40 border-b border-slate-800/80 flex-shrink-0">
        <div className="bg-slate-950/60 p-2.5 rounded-xl border border-slate-800/80">
          <span className="text-[10px] text-slate-500 font-medium uppercase tracking-wider block">Units Sold</span>
          <span className="text-base font-bold font-mono text-slate-100">{stats.unitsSold}</span>
        </div>
        <div className="bg-slate-950/60 p-2.5 rounded-xl border border-slate-800/80">
          <span className="text-[10px] text-emerald-500/80 font-medium uppercase tracking-wider block">Total Sales Value</span>
          <span className="text-base font-bold font-mono text-emerald-400">{formatPaise(stats.revenuePaise)}</span>
        </div>
        <div className="bg-slate-950/60 p-2.5 rounded-xl border border-slate-800/80">
          <span className="text-[10px] text-sky-500/80 font-medium uppercase tracking-wider block">Average Unit Price</span>
          <span className="text-base font-bold font-mono text-sky-400">{formatPaise(stats.avgPrice)}</span>
        </div>
        <div className="bg-slate-950/60 p-2.5 rounded-xl border border-slate-800/80">
          <span className="text-[10px] text-slate-400 font-medium uppercase tracking-wider block">Current Stock</span>
          <span className="text-base font-bold font-mono text-slate-200">
            {stats.currentStock != null ? `${stats.currentStock} in stock` : `${products.length} products total`}
          </span>
        </div>
      </div>

      {/* Main Flow Table */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          data={flowItems}
          keyFn={(r) => r.id}
          searchKeys={['product_name', 'product_sku', 'invoice_number', 'customer_name']}
          pageSize={25}
          emptyMessage="No product flow events found for the selected filter."
          isLoading={isLoading}
        />
      </div>
    </div>
  );
}
