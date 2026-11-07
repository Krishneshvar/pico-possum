import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowRightLeft, ArrowDownRight, ArrowUpRight, RefreshCw, Calendar, Receipt, ExternalLink } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatDateTime } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';
import { useWorkspaceStore } from '../../../stores/workspaceStore';

export default function StockLedgerView() {
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const { openOrFocus } = useWorkspaceStore();

  const { data: movements = [], isLoading, refetch } = useQuery({
    queryKey: ['stock_movements', fromDate, toDate],
    queryFn: () => tauriApi.getStockMovements(null, fromDate || null, toDate || null, 500),
  });

  const movementCounts = useMemo(() => {
    const counts = {};
    for (const m of movements) {
      if (m.movement_type) {
        counts[m.movement_type] = (counts[m.movement_type] || 0) + 1;
      }
    }
    return counts;
  }, [movements]);

  const movementTypeOptions = useMemo(
    () => [
      { value: 'SALE', label: `Sale (${movementCounts.SALE || 0})` },
      { value: 'PURCHASE', label: `Purchase (${movementCounts.PURCHASE || 0})` },
      { value: 'RESTOCK', label: `Restock (${movementCounts.RESTOCK || 0})` },
      { value: 'MANUAL_ADJUSTMENT', label: `Manual Adjustment (${movementCounts.MANUAL_ADJUSTMENT || 0})` },
      { value: 'RETURN', label: `Return (${movementCounts.RETURN || 0})` },
    ],
    [movementCounts]
  );

  const columns = [
    {
      key: 'created_at',
      label: 'Date & Time',
      className: 'font-mono text-text-muted text-[11px] whitespace-nowrap',
      render: (r) => formatDateTime(r.created_at),
    },
    {
      key: 'product_sku',
      label: 'SKU',
      className: 'font-mono text-text-muted text-[11px]',
      render: (r) => r.product_sku || <span className="text-text-muted">—</span>,
    },
    {
      key: 'product_name',
      label: 'Product',
      className: 'font-semibold text-text-primary text-xs',
      render: (r) => r.product_name || <span className="text-text-muted italic">Unknown Item</span>,
    },
    {
      key: 'movement_type',
      label: 'Movement Type',
      headerClassName: 'text-center',
      className: 'text-center text-xs',
      render: (r) => (
        <span className="text-[10px] font-semibold px-2 py-0.5 rounded-md bg-surface-secondary text-text-secondary border border-border-default uppercase">
          {r.movement_type}
        </span>
      ),
    },
    {
      key: 'quantity_change',
      label: 'Quantity Delta',
      headerClassName: 'text-center',
      className: 'text-center financial-value font-bold text-xs',
      render: (r) => {
        const delta = r.quantity_change;
        const pos = delta > 0;
        const zero = delta === 0;
        return (
          <span
            className={`inline-flex items-center gap-1 font-mono font-bold ${
              pos ? 'text-success' : zero ? 'text-text-muted' : 'text-danger'
            }`}
          >
            {pos ? (
              <ArrowUpRight className="w-3.5 h-3.5" />
            ) : zero ? null : (
              <ArrowDownRight className="w-3.5 h-3.5" />
            )}
            {pos ? `+${delta}` : delta}
          </span>
        );
      },
    },
    {
      key: 'reference_id',
      label: 'Reference / Invoice',
      className: 'font-mono text-xs',
      render: (r) => {
        const isInvoiceRef =
          r.reference_id && (r.movement_type === 'SALE' || r.movement_type === 'RETURN');
        if (!r.reference_id) return <span className="text-text-muted">—</span>;
        if (isInvoiceRef) {
          return (
            <button
              type="button"
              onClick={() =>
                openOrFocus(`Bill #${r.reference_id}`, 'bill-detail', {
                  invoiceNumber: r.reference_id,
                })
              }
              className="inline-flex items-center gap-1 text-brand hover:underline font-mono text-[11px] cursor-pointer"
              title={`Open bill ${r.reference_id}`}
            >
              <Receipt className="w-3 h-3 shrink-0" />
              <span>{r.reference_id}</span>
              <ExternalLink className="w-2.5 h-2.5 opacity-60" />
            </button>
          );
        }
        return <span className="text-text-muted text-[11px]">{r.reference_id}</span>;
      },
    },
    {
      key: 'note',
      label: 'Audit Reason / Notes',
      className: 'text-text-secondary text-xs',
      render: (r) => r.note || <span className="text-text-muted">—</span>,
    },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas overflow-hidden">
      {/* Header */}
      <div className="h-14 px-5 border-b border-border-default bg-surface flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-brand-subtle text-brand flex items-center justify-center">
            <ArrowRightLeft className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-text-primary">Stock Activity Ledger</h2>
            <p className="text-[11px] text-text-muted">
              Inventory movements, restocking history, audits, and sales ledger
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={() => refetch()}
          className="p-1.5 rounded-lg border border-border-default text-text-muted hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
          title="Refresh Ledger"
        >
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      {/* Date Filter Toolbar */}
      <div className="px-5 py-2.5 border-b border-border-default bg-surface flex items-center gap-3 flex-wrap text-xs shrink-0">
        <div className="flex items-center gap-1.5 text-text-muted">
          <Calendar className="w-3.5 h-3.5 text-brand" />
          <span className="font-medium text-text-secondary">Date Filter:</span>
        </div>

        <div className="flex items-center gap-1.5">
          <span className="text-text-muted">From:</span>
          <input
            type="date"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            className="bg-surface-secondary border border-border-strong text-text-primary rounded-lg px-2 py-1 focus:border-brand focus:outline-none font-mono text-xs"
          />
        </div>

        <div className="flex items-center gap-1.5">
          <span className="text-text-muted">To:</span>
          <input
            type="date"
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            className="bg-surface-secondary border border-border-strong text-text-primary rounded-lg px-2 py-1 focus:border-brand focus:outline-none font-mono text-xs"
          />
        </div>

        {(fromDate || toDate) && (
          <button
            type="button"
            onClick={() => {
              setFromDate('');
              setToDate('');
            }}
            className="text-xs text-brand hover:underline px-2 py-1 cursor-pointer"
          >
            Clear Dates
          </button>
        )}
      </div>

      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          persistKey="stock-ledger"
          data={movements}
          keyFn={(r) => r.id}
          searchKeys={['product_name', 'movement_type', 'note', 'reference_id']}
          filters={[
            {
              type: 'multiselect',
              key: 'movement_type',
              label: 'Movement Types',
              options: movementTypeOptions,
            },
          ]}
          isLoading={isLoading}
          emptyMessage="No stock movements found for the selected period."
        />
      </div>
    </div>
  );
}
