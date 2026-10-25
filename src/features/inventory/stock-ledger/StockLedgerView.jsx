import { useQuery } from '@tanstack/react-query';
import { ArrowRightLeft, ArrowDownRight, ArrowUpRight } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatDateTime } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';

const TYPE_COLORS = {
  SALE:              'bg-rose-500/15 text-rose-400',
  PURCHASE:          'bg-emerald-500/15 text-emerald-400',
  MANUAL_ADJUSTMENT: 'bg-blue-500/15 text-blue-400',
  RETURN:            'bg-amber-500/15 text-amber-400',
};

export default function StockLedgerView() {
  const { data: movements = [], isLoading } = useQuery({
    queryKey: ['stock_movements'],
    queryFn: () => tauriApi.getStockMovements(null, 500),
  });

  const columns = [
    {
      key: 'created_at', label: 'Date & Time',
      className: 'font-mono text-slate-400 text-[11px] whitespace-nowrap',
      render: (r) => formatDateTime(r.created_at),
    },
    {
      key: 'product_name', label: 'Product',
      className: 'font-semibold text-slate-200',
      render: (r) => r.product_name || <span className="text-slate-500 italic">Unknown</span>,
    },
    {
      key: 'movement_type', label: 'Type',
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => (
        <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${TYPE_COLORS[r.movement_type] ?? 'bg-slate-800 text-slate-300'}`}>
          {r.movement_type}
        </span>
      ),
    },
    {
      key: 'quantity_change', label: 'Qty Change',
      headerClassName: 'text-center',
      className: 'text-center font-mono font-bold',
      render: (r) => {
        const pos = r.quantity_change > 0;
        return (
          <span className={`inline-flex items-center gap-1 ${pos ? 'text-emerald-400' : 'text-rose-400'}`}>
            {pos ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
            {pos ? `+${r.quantity_change}` : r.quantity_change}
          </span>
        );
      },
    },
    {
      key: 'reference_id', label: 'Reference',
      className: 'font-mono text-slate-500 text-[11px]',
      render: (r) => r.reference_id || <span className="text-slate-700">—</span>,
    },
    {
      key: 'note', label: 'Notes',
      className: 'text-slate-400',
      render: (r) => r.note || <span className="text-slate-700">—</span>,
    },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-14 px-5 border-b border-slate-800/80 bg-slate-900/50 flex items-center flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <ArrowRightLeft className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-100">Stock Activity Ledger</h2>
            <p className="text-[11px] text-slate-500">Immutable trail of all inventory events and adjustments</p>
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          data={movements}
          keyFn={(r) => r.id}
          searchKeys={['product_name', 'movement_type', 'note', 'reference_id']}
          filters={[
            {
              key: 'movement_type',
              label: 'All Types',
              options: [
                { value: 'SALE', label: 'Sale' },
                { value: 'PURCHASE', label: 'Purchase' },
                { value: 'MANUAL_ADJUSTMENT', label: 'Manual Adjustment' },
                { value: 'RETURN', label: 'Return' },
              ],
            },
          ]}
          isLoading={isLoading}
          emptyMessage="No stock movements recorded yet."
        />
      </div>
    </div>
  );
}
