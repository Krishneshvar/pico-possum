import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { History, Search, Eye, Printer, RotateCcw, X, CheckCircle2, AlertTriangle } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise, formatDateTime } from '../../../lib/formatters';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';

export default function BillsHistoryView() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [selectedSaleId, setSelectedSaleId] = useState(null);
  const [reprintSale, setReprintSale] = useState(null);
  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('Customer return / refund');

  // Queries
  const { data: sales = [], isLoading } = useQuery({
    queryKey: ['sales', search],
    queryFn: () => tauriApi.getSales(50, 0, search || null),
  });

  const { data: saleDetails, isLoading: loadingDetails } = useQuery({
    queryKey: ['sale_details', selectedSaleId],
    queryFn: () => tauriApi.getSaleDetails(selectedSaleId),
    enabled: !!selectedSaleId,
  });

  const { data: billSettings } = useQuery({
    queryKey: ['bill_settings'],
    queryFn: () => tauriApi.getBillSettings(),
  });

  const { data: generalSettings } = useQuery({
    queryKey: ['general_settings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });

  // Cancel sale mutation
  const cancelMutation = useMutation({
    mutationFn: ({ id, reason }) => tauriApi.cancelSale(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sales'] });
      queryClient.invalidateQueries({ queryKey: ['sale_details'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['stock_movements'] });
      setCancelModalOpen(false);
      setSelectedSaleId(null);
      alert('Sale cancelled and items returned to inventory successfully.');
    },
    onError: (err) => alert(`Failed to cancel sale: ${err}`),
  });

  const handleReprint = (details) => {
    setReprintSale(details);
    setTimeout(() => {
      window.print();
    }, 100);
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-16 px-6 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <History className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-100">Bill History & Sales Ledger</h2>
            <p className="text-xs text-slate-400">View past sales, reprint receipts, and process customer returns</p>
          </div>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="p-4 border-b border-slate-800/80 bg-slate-900/30 flex items-center gap-4">
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            type="text"
            placeholder="Search by invoice number or customer name..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-9 pr-4 py-2 rounded-xl bg-slate-900 border border-slate-800 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-emerald-500 font-mono"
          />
        </div>
      </div>

      {/* Sales Table */}
      <div className="flex-1 overflow-y-auto">
        <table className="w-full text-left border-collapse">
          <thead className="bg-slate-900/80 text-[11px] font-semibold text-slate-400 uppercase tracking-wider sticky top-0 border-b border-slate-800 z-10">
            <tr>
              <th className="py-3 px-4">Invoice #</th>
              <th className="py-3 px-4">Date & Time</th>
              <th className="py-3 px-4">Customer</th>
              <th className="py-3 px-4">Payment</th>
              <th className="py-3 px-4 text-right">Total</th>
              <th className="py-3 px-4 text-center">Status</th>
              <th className="py-3 px-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/50 text-xs">
            {sales.length === 0 ? (
              <tr>
                <td colSpan="7" className="py-12 text-center text-slate-500">
                  No sales recorded.
                </td>
              </tr>
            ) : (
              sales.map((s) => {
                const isPaid = s.status === 'PAID';
                const isCancelled = s.status === 'CANCELLED';

                return (
                  <tr key={s.id} className="hover:bg-slate-900/40 transition-colors">
                    <td className="py-3 px-4 font-mono font-bold text-slate-100">{s.invoice_number}</td>
                    <td className="py-3 px-4 text-slate-400 font-mono">{formatDateTime(s.created_at)}</td>
                    <td className="py-3 px-4 text-slate-300">
                      <div>{s.customer_name || 'Walk-in'}</div>
                      {s.customer_phone && <div className="text-[11px] text-slate-500 font-mono">{s.customer_phone}</div>}
                    </td>
                    <td className="py-3 px-4">
                      <span className="text-[11px] font-semibold px-2 py-0.5 rounded bg-slate-800 text-slate-300">
                        {s.payment_method}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right font-mono font-bold text-slate-100">
                      {formatPaise(s.total_paise)}
                    </td>
                    <td className="py-3 px-4 text-center">
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold ${
                          isCancelled
                            ? 'bg-rose-500/15 text-rose-400 border border-rose-500/30'
                            : 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                        }`}
                      >
                        {s.status}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right">
                      <button
                        onClick={() => setSelectedSaleId(s.id)}
                        className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium inline-flex items-center gap-1.5 transition-colors"
                      >
                        <Eye className="w-3.5 h-3.5 text-emerald-400" />
                        <span>View</span>
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* ── Bill Details Modal ────────────────────────────────────────── */}
      {selectedSaleId && saleDetails && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 shadow-2xl space-y-4 max-h-[90vh] flex flex-col">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <div>
                <h3 className="font-bold text-base text-slate-100 flex items-center gap-2">
                  <span>Bill #{saleDetails.sale.invoice_number}</span>
                  <span
                    className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                      saleDetails.sale.status === 'CANCELLED'
                        ? 'bg-rose-500/20 text-rose-400'
                        : 'bg-emerald-500/20 text-emerald-400'
                    }`}
                  >
                    {saleDetails.sale.status}
                  </span>
                </h3>
                <p className="text-xs text-slate-400 mt-0.5 font-mono">
                  {formatDateTime(saleDetails.sale.created_at)}
                </p>
              </div>
              <button onClick={() => setSelectedSaleId(null)} className="text-slate-400 hover:text-white">
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Customer & Payment Info */}
            <div className="grid grid-cols-2 gap-3 text-xs bg-slate-950/60 p-3 rounded-xl border border-slate-800">
              <div>
                <span className="text-slate-500 block">Customer</span>
                <span className="font-semibold text-slate-200">
                  {saleDetails.sale.customer_name || 'Walk-in Customer'}
                </span>
                {saleDetails.sale.customer_phone && (
                  <span className="text-slate-400 block font-mono">{saleDetails.sale.customer_phone}</span>
                )}
              </div>
              <div>
                <span className="text-slate-500 block">Payment Method</span>
                <span className="font-semibold text-slate-200">{saleDetails.sale.payment_method}</span>
                <span className="text-slate-400 block">Paid: {formatPaise(saleDetails.sale.paid_paise)}</span>
              </div>
            </div>

            {/* Line Items */}
            <div className="flex-1 overflow-y-auto border border-slate-800 rounded-xl divide-y divide-slate-800/60">
              {saleDetails.items.map((it) => (
                <div key={it.id} className="p-3 text-xs flex justify-between items-center">
                  <div>
                    <div className="font-semibold text-slate-200">{it.product_name}</div>
                    <div className="text-[11px] text-slate-400 font-mono">
                      {it.quantity} x {formatPaise(it.unit_price_paise)}
                    </div>
                  </div>
                  <div className="font-bold text-slate-100 font-mono">
                    {formatPaise(it.total_paise)}
                  </div>
                </div>
              ))}
            </div>

            {/* Totals Summary */}
            <div className="space-y-1.5 text-xs text-slate-300 pt-2 border-t border-slate-800">
              <div className="flex justify-between">
                <span>Subtotal</span>
                <span className="font-mono">{formatPaise(saleDetails.sale.subtotal_paise)}</span>
              </div>
              {saleDetails.sale.discount_paise > 0 && (
                <div className="flex justify-between text-amber-400">
                  <span>Discount</span>
                  <span className="font-mono">-{formatPaise(saleDetails.sale.discount_paise)}</span>
                </div>
              )}
              <div className="flex justify-between text-base font-bold text-slate-100 pt-1 border-t border-slate-800">
                <span>Total</span>
                <span className="font-mono text-emerald-400">{formatPaise(saleDetails.sale.total_paise)}</span>
              </div>
            </div>

            {/* Actions */}
            <div className="flex items-center justify-between pt-3 border-t border-slate-800">
              {saleDetails.sale.status !== 'CANCELLED' ? (
                <button
                  onClick={() => setCancelModalOpen(true)}
                  className="px-3 py-1.5 rounded-lg text-rose-400 hover:bg-rose-500/10 text-xs font-semibold flex items-center gap-1.5 transition-colors border border-rose-500/20"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Cancel / Return</span>
                </button>
              ) : (
                <div className="text-xs text-rose-400 font-semibold">Cancelled</div>
              )}

              <div className="flex gap-2">
                <button
                  onClick={() => handleReprint(saleDetails)}
                  className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-bold flex items-center gap-2 border border-slate-700 transition-colors"
                >
                  <Printer className="w-4 h-4" />
                  <span>Reprint Receipt</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Hidden print container */}
      {reprintSale && (
        <div className="hidden">
          <ReceiptPrinter
            sale={reprintSale.sale}
            items={reprintSale.items}
            billSettings={billSettings}
            generalSettings={generalSettings}
          />
        </div>
      )}

      {/* Cancel Confirmation Modal */}
      {cancelModalOpen && selectedSaleId && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-2xl">
            <div className="flex items-center gap-3 text-rose-400">
              <AlertTriangle className="w-6 h-6" />
              <h3 className="font-bold text-sm text-slate-100">Cancel & Return Sale</h3>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">
              This will mark the sale as CANCELLED and automatically restore all items back into your inventory ledger.
            </p>
            <div>
              <label className="text-xs text-slate-400 block mb-1">Reason for Return</label>
              <input
                type="text"
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-xs text-slate-200 focus:outline-none focus:border-rose-500"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setCancelModalOpen(false)}
                className="px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:bg-slate-800"
              >
                Back
              </button>
              <button
                onClick={() => cancelMutation.mutate({ id: selectedSaleId, reason: cancelReason })}
                disabled={cancelMutation.isPending}
                className="px-4 py-1.5 rounded-lg text-xs font-bold bg-rose-500 hover:bg-rose-400 text-white shadow-md shadow-rose-500/20"
              >
                Confirm Cancellation
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
