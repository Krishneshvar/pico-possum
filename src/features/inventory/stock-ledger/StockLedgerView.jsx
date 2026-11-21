import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowRightLeft, ArrowDownRight, ArrowUpRight, RefreshCw, Receipt, ExternalLink } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatDateTime } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';
import { useWorkspaceStore } from '../../../stores/workspaceStore';
import { strings } from '../../../lib/strings';

const s = strings.stockLedger;

const REASON_CONFIG = {
  MANUAL_STOCK_CORRECTION: {
    label: s.movementTypes.manualStockCorrection,
    badgeClass: 'bg-amber-500/10 text-amber-500 border-amber-500/20',
  },
  PURCHASE: {
    label: s.movementTypes.purchase,
    badgeClass: 'bg-blue-500/10 text-blue-500 border-blue-500/20',
  },
  BILL_ADJUSTMENT: {
    label: s.movementTypes.billAdjustment,
    badgeClass: 'bg-purple-500/10 text-purple-500 border-purple-500/20',
  },
  RETURN: {
    label: s.movementTypes.return,
    badgeClass: 'bg-teal-500/10 text-teal-500 border-teal-500/20',
  },
  CANCELLED: {
    label: s.movementTypes.cancelled,
    badgeClass: 'bg-rose-500/10 text-rose-500 border-rose-500/20',
  },
  SALE: {
    label: s.movementTypes.sale,
    badgeClass: 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20',
  },
  PRODUCT_DELETED: {
    label: s.movementTypes.productDeleted || 'Product Deleted',
    badgeClass: 'bg-rose-500/10 text-rose-500 border-rose-500/20',
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
  if (upper === 'PRODUCT_DELETED' || upper === 'DELETED') return 'PRODUCT_DELETED';
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
      { value: 'MANUAL_STOCK_CORRECTION', label: s.movementTypes.manualStockCorrection },
      { value: 'PURCHASE', label: s.movementTypes.purchase },
      { value: 'BILL_ADJUSTMENT', label: s.movementTypes.billAdjustment },
      { value: 'RETURN', label: s.movementTypes.return },
      { value: 'CANCELLED', label: s.movementTypes.cancelled },
      { value: 'SALE', label: s.movementTypes.sale },
      { value: 'PRODUCT_DELETED', label: s.movementTypes.productDeleted || 'Product Deleted' },
    ],
    []
  );

  const columns = [
    {
      key: 'created_at',
      label: s.columns.dateTime,
      className: 'font-mono text-text-muted text-[11px] whitespace-nowrap',
      render: (r) => formatDateTime(r.created_at),
    },
    {
      key: 'product_sku',
      label: s.columns.sku,
      className: 'font-mono text-text-muted text-[11px]',
      render: (r) => r.product_sku || <span className="text-text-muted">—</span>,
    },
    {
      key: 'product_name',
      label: s.columns.product,
      className: 'font-semibold text-text-primary text-xs',
      render: (r) => (
        <span className="inline-flex items-center gap-1.5">
          <span>{r.product_name || <span className="text-text-muted italic">{s.unknownItem}</span>}</span>
          {r.movement_type === 'PRODUCT_DELETED' && (
            <span className="text-[10px] text-text-muted font-normal italic">(Deleted)</span>
          )}
        </span>
      ),
    },
    {
      key: 'movement_type',
      label: s.columns.reason,
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
      label: s.columns.quantityDelta,
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
      label: s.columns.reference,
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
                openOrFocus(s.billTabTitle(r.reference_id), 'bill-detail', {
                  invoiceNumber: r.reference_id,
                })
              }
              className="inline-flex items-center gap-1 text-brand hover:underline font-mono text-[11px] cursor-pointer"
              title={s.openBill(r.reference_id)}
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
      label: s.columns.notes,
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
            <h2 className="text-sm font-bold text-text-primary">{s.title}</h2>
            <p className="text-[11px] text-text-muted">
              {s.subtitle}
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={() => refetch()}
          className="p-1.5 rounded-lg border border-border-default text-text-muted hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
          title={s.refresh}
        >
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
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
              label: s.filters.reasons,
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
              label: s.filters.date,
              fromVal: fromDate,
              toVal: toDate,
              onFromChange: (val) => setFromDate(val),
              onToChange: (val) => setToDate(val),
            },
          ]}
          isLoading={isLoading}
          emptyMessage={s.empty}
        />
      </div>
    </div>
  );
}
