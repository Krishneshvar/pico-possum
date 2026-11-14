import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowRightLeft, ArrowDownRight, ArrowUpRight, RefreshCw, Receipt, ExternalLink } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatDateTime } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';
import { useWorkspaceStore } from '../../../stores/workspaceStore';

const REASON_CONFIG = {
  MANUAL_STOCK_CORRECTION: {
    label: 'Manual Stock Correction',
    badgeClass: 'bg-amber-500/10 text-amber-500 border-amber-500/20',
  },
  PURCHASE: {
    label: 'Purchase',
    badgeClass: 'bg-blue-500/10 text-blue-500 border-blue-500/20',
  },
  BILL_ADJUSTMENT: {
    label: 'Bill Adjustment',
    badgeClass: 'bg-purple-500/10 text-purple-500 border-purple-500/20',
  },
  RETURN: {
    label: 'Return',
    badgeClass: 'bg-teal-500/10 text-teal-500 border-teal-500/20',
  },
  CANCELLED: {
    label: 'Cancelled',
    badgeClass: 'bg-rose-500/10 text-rose-500 border-rose-500/20',
  },
  SALE: {
    label: 'Sale',
    badgeClass: 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20',
  },
};

function normalizeMovementType(rawType) {
  if (!rawType) return 'MANUAL_STOCK_CORRECTION';
  const upper = String(rawType).toUpperCase();
  if (upper === 'PURCHASE' || upper === 'RESTOCK') return 'PURCHASE';
  if (upper === 'BILL_ADJUSTMENT') return 'BILL_ADJUSTMENT';
  if (upper === 'RETURN') return 'RETURN';
  if (upper === 'CANCELLED' || upper === 'CANCELED') return 'CANCELLED';
  if (upper === 'SALE') return 'SALE';
  if (
    upper === 'MANUAL_STOCK_CORRECTION' ||
    upper === 'MANUAL_ADJUSTMENT' ||
    upper === 'ADJUSTMENT'
  ) {
    return 'MANUAL_STOCK_CORRECTION';
  }
  return upper;
}

export default function StockLedgerView() {
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const { openOrFocus } = useWorkspaceStore();

  const { data: movements = [], isLoading, refetch } = useQuery({
    queryKey: ['stock_movements', fromDate, toDate],
    queryFn: () => tauriApi.getStockMovements(null, fromDate || null, toDate || null, 500),
  });

  const movementTypeOptions = useMemo(
    () => [
      { value: 'MANUAL_STOCK_CORRECTION', label: 'Manual Stock Correction' },
      { value: 'PURCHASE', label: 'Purchase' },
      { value: 'BILL_ADJUSTMENT', label: 'Bill Adjustment' },
      { value: 'RETURN', label: 'Return' },
      { value: 'CANCELLED', label: 'Cancelled' },
      { value: 'SALE', label: 'Sale' },
    ],
    []
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
      label: 'Stock Change Reason',
      headerClassName: 'text-center',
      className: 'text-center text-xs',
      render: (r) => {
        const norm = normalizeMovementType(r.movement_type);
        const config = REASON_CONFIG[norm] || {
          label: r.movement_type,
          badgeClass: 'bg-surface-secondary text-text-secondary border-border-default',
        };
        return (
          <span
            className={`text-[10px] font-semibold px-2 py-0.5 rounded-md border ${config.badgeClass}`}
          >
            {config.label}
          </span>
        );
      },
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
          r.reference_id &&
          (r.movement_type === 'SALE' ||
            r.movement_type === 'RETURN' ||
            r.movement_type === 'CANCELLED' ||
            r.movement_type === 'BILL_ADJUSTMENT');
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
              label: 'Stock Change Reasons',
              options: movementTypeOptions,
              filterFn: (row, selectedValues) => {
                if (!selectedValues || selectedValues.length === 0) return true;
                const norm = normalizeMovementType(row.movement_type);
                return selectedValues.includes(norm);
              },
            },
            {
              type: 'daterange',
              fromKey: 'created_at',
              toKey: 'created_at',
              label: 'Date Filter',
              fromVal: fromDate,
              toVal: toDate,
              onFromChange: (val) => setFromDate(val),
              onToChange: (val) => setToDate(val),
            },
          ]}
          isLoading={isLoading}
          emptyMessage="No stock movements found for the selected period."
        />
      </div>
    </div>
  );
}
