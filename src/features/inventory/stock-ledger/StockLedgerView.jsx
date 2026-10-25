import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowRightLeft, ArrowDownRight, ArrowUpRight, ShieldCheck } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatDateTime } from '../../../lib/formatters';

export default function StockLedgerView() {
  const { data: movements = [], isLoading } = useQuery({
    queryKey: ['stock_movements'],
    queryFn: () => tauriApi.getStockMovements(null, 100),
  });

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-16 px-6 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <ArrowRightLeft className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-100">Stock Activity Ledger</h2>
            <p className="text-xs text-slate-400">Immutable ledger trail of all inventory events and adjustments</p>
          </div>
        </div>
      </div>

      {/* Ledger Table */}
      <div className="flex-1 overflow-y-auto">
        <table className="w-full text-left border-collapse">
          <thead className="bg-slate-900/80 text-[11px] font-semibold text-slate-400 uppercase tracking-wider sticky top-0 border-b border-slate-800 z-10">
            <tr>
              <th className="py-3 px-4">Date & Time</th>
              <th className="py-3 px-4">Product</th>
              <th className="py-3 px-4 text-center">Type</th>
              <th className="py-3 px-4 text-center">Change</th>
              <th className="py-3 px-4">Reference</th>
              <th className="py-3 px-4">Notes</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/50 text-xs font-mono">
            {movements.length === 0 ? (
              <tr>
                <td colSpan="6" className="py-12 text-center text-slate-500 font-sans">
                  No stock movements recorded yet.
                </td>
              </tr>
            ) : (
              movements.map((m) => {
                const isPositive = m.quantity_change > 0;
                return (
                  <tr key={m.id} className="hover:bg-slate-900/40 transition-colors">
                    <td className="py-3 px-4 text-slate-400">{formatDateTime(m.created_at)}</td>
                    <td className="py-3 px-4 font-semibold text-slate-200 font-sans">{m.product_name || 'Product'}</td>
                    <td className="py-3 px-4 text-center">
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-slate-800 text-slate-300">
                        {m.movement_type}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-center">
                      <span
                        className={`inline-flex items-center gap-1 font-bold ${
                          isPositive ? 'text-emerald-400' : 'text-rose-400'
                        }`}
                      >
                        {isPositive ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
                        {isPositive ? `+${m.quantity_change}` : m.quantity_change}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-400 truncate max-w-[120px]">{m.reference_id || '-'}</td>
                    <td className="py-3 px-4 text-slate-400 font-sans">{m.note || '-'}</td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
