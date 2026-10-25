import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft,
  Printer,
  RotateCcw,
  AlertTriangle,
  Receipt,
  User,
  CreditCard,
  Calendar,
  CheckCircle2,
  XCircle,
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise, formatDateTime } from '../../../lib/formatters';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';
import { useWorkspaceStore } from '../../../stores/workspaceStore';

export default function BillDetailView({ saleId, invoiceNumber }) {
  const queryClient = useQueryClient();
  const { openOrFocus, closeTab, activeTabId } = useWorkspaceStore();
  const [reprintSale, setReprintSale] = useState(null);
  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('Customer return / refund');

  const { data: saleDetails, isLoading, isError } = useQuery({
    queryKey: ['sale_details', saleId],
    queryFn: () => tauriApi.getSaleDetails(saleId),
    enabled: !!saleId,
  });

  const { data: billSettings } = useQuery({
    queryKey: ['bill_settings'],
    queryFn: () => tauriApi.getBillSettings(),
  });

  const { data: generalSettings } = useQuery({
    queryKey: ['general_settings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });

  const cancelMutation = useMutation({
    mutationFn: ({ id, reason }) => tauriApi.cancelSale(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sales'] });
      queryClient.invalidateQueries({ queryKey: ['sale_details', saleId] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['stock_movements'] });
      setCancelModalOpen(false);
    },
    onError: (err) => alert(`Failed to cancel sale: ${err}`),
  });

  const handleReprint = () => {
    if (!saleDetails) return;
    setReprintSale(saleDetails);
    setTimeout(() => window.print(), 100);
  };

  const handleBack = () => {
    // Navigate back to Bill History tab and close this tab
    openOrFocus('Bill History', 'bills');
    if (activeTabId) {
      closeTab(activeTabId);
    }
  };

  if (isLoading) {
    return (
      <div className="flex-1 flex items-center justify-center bg-slate-950 text-slate-500">
        <div className="flex items-center gap-3">
          <div className="w-5 h-5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
          <span className="text-sm">Loading bill details...</span>
        </div>
      </div>
    );
  }

  if (isError || !saleDetails) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center bg-slate-950 text-slate-400 gap-3">
        <Receipt className="w-10 h-10 text-slate-600" />
        <p className="text-sm">Could not find bill details for ID: {saleId}</p>
        <button
          onClick={handleBack}
          className="px-3.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold"
        >
          Return to Bill History
        </button>
      </div>
    );
  }

  const { sale, items } = saleDetails;
  const isCancelled = sale.status === 'CANCELLED';

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* ── Top Bar ── */}
      <div className="h-14 px-6 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-4">
          <button
            onClick={handleBack}
            className="p-1.5 rounded-lg bg-slate-800/80 hover:bg-slate-700 text-slate-400 hover:text-white transition-colors"
            title="Back to Bill History"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
              <Receipt className="w-4 h-4" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h2 className="text-sm font-bold text-slate-100 font-mono tracking-wide">
                  Bill #{sale.invoice_number || invoiceNumber}
                </h2>
                <span
                  className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider border ${
                    isCancelled
                      ? 'bg-rose-500/15 text-rose-400 border-rose-500/30'
                      : 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30'
                  }`}
                >
                  {isCancelled ? <XCircle className="w-3 h-3" /> : <CheckCircle2 className="w-3 h-3" />}
                  {sale.status}
                </span>
              </div>
              <p className="text-[11px] text-slate-500 font-mono">
                {formatDateTime(sale.created_at)}
              </p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {!isCancelled && (
            <button
              onClick={() => setCancelModalOpen(true)}
              className="px-3.5 py-1.5 rounded-lg text-rose-400 hover:bg-rose-500/10 text-xs font-semibold flex items-center gap-1.5 transition-colors border border-rose-500/20 hover:border-rose-500/40"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              Cancel / Return Sale
            </button>
          )}
          <button
            onClick={handleReprint}
            className="px-4 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-emerald-500/20 transition-all"
          >
            <Printer className="w-3.5 h-3.5" />
            Reprint Receipt
          </button>
        </div>
      </div>

      {/* ── Main Content Area ── */}
      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        {/* Cancelled Banner */}
        {isCancelled && (
          <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
            <div className="text-xs">
              <h4 className="font-bold text-rose-300">This sale has been voided / cancelled</h4>
              <p className="text-rose-400/80 mt-0.5">
                All inventory quantities have been restored to stock, and the invoice has been recorded as returned.
              </p>
            </div>
          </div>
        )}

        {/* Info Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* Customer Card */}
          <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
              <User className="w-4 h-4 text-emerald-400" />
              Customer Information
            </div>
            <div>
              <div className="text-sm font-bold text-slate-200">
                {sale.customer_name || 'Walk-in Customer'}
              </div>
              {sale.customer_phone ? (
                <div className="text-xs text-slate-400 font-mono mt-0.5">{sale.customer_phone}</div>
              ) : (
                <div className="text-xs text-slate-600 italic mt-0.5">No contact number provided</div>
              )}
            </div>
          </div>

          {/* Payment Card */}
          <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
              <CreditCard className="w-4 h-4 text-cyan-400" />
              Payment Details
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold px-2 py-0.5 rounded bg-slate-800 text-slate-200 uppercase">
                  {sale.payment_method}
                </span>
                <span className="text-xs text-slate-400">
                  Paid: <strong className="text-slate-200 font-mono">{formatPaise(sale.paid_paise)}</strong>
                </span>
              </div>
              <div className="text-[11px] text-slate-500 mt-1">
                Biller: <span className="text-slate-400">{sale.biller_name || 'Counter Terminal'}</span>
              </div>
            </div>
          </div>

          {/* Invoice Summary Card */}
          <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
              <Calendar className="w-4 h-4 text-amber-400" />
              Transaction Timestamp
            </div>
            <div>
              <div className="text-xs font-mono font-bold text-slate-200">
                {formatDateTime(sale.created_at)}
              </div>
              <div className="text-[11px] text-slate-500 mt-1 font-mono">
                Total Items: <span className="text-slate-300 font-bold">{items.length} item line{items.length !== 1 ? 's' : ''}</span>
              </div>
            </div>
          </div>
        </div>

        {/* ── Line Items Table ── */}
        <div className="rounded-xl border border-slate-800 overflow-hidden bg-slate-900/40">
          <div className="px-4 py-3 border-b border-slate-800 bg-slate-900/80 flex items-center justify-between">
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-300">
              Itemized Line Items
            </h3>
            <span className="text-[11px] text-slate-500 font-mono">
              {items.reduce((acc, it) => acc + (it.quantity || 0), 0)} total units
            </span>
          </div>

          <table className="w-full text-left border-collapse text-xs">
            <thead className="bg-slate-900/60 text-[11px] font-semibold text-slate-400 uppercase tracking-wider border-b border-slate-800">
              <tr>
                <th className="py-2.5 px-4 w-12 text-slate-500">#</th>
                <th className="py-2.5 px-4">Product Name</th>
                <th className="py-2.5 px-4 text-right">Unit Price</th>
                <th className="py-2.5 px-4 text-center">Qty</th>
                <th className="py-2.5 px-4 text-right">Tax Rate</th>
                <th className="py-2.5 px-4 text-right">Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {items.map((it, idx) => (
                <tr key={it.id || idx} className="hover:bg-slate-800/30 transition-colors">
                  <td className="py-3 px-4 text-slate-500 font-mono">{idx + 1}</td>
                  <td className="py-3 px-4">
                    <div className="font-semibold text-slate-200">{it.product_name}</div>
                  </td>
                  <td className="py-3 px-4 text-right font-mono text-slate-300">
                    {formatPaise(it.unit_price_paise)}
                  </td>
                  <td className="py-3 px-4 text-center font-mono font-bold text-slate-200">
                    {it.quantity}
                  </td>
                  <td className="py-3 px-4 text-right font-mono text-slate-400">
                    {it.tax_rate != null ? `${it.tax_rate}%` : '0%'}
                  </td>
                  <td className="py-3 px-4 text-right font-mono font-bold text-slate-100">
                    {formatPaise(it.total_paise)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* ── Totals Calculation Footer ── */}
          <div className="p-4 bg-slate-900/90 border-t border-slate-800 flex justify-end">
            <div className="w-72 space-y-2 text-xs">
              <div className="flex justify-between text-slate-400">
                <span>Subtotal</span>
                <span className="font-mono text-slate-200">{formatPaise(sale.subtotal_paise)}</span>
              </div>
              {sale.discount_paise > 0 && (
                <div className="flex justify-between text-amber-400 font-semibold">
                  <span>Discount</span>
                  <span className="font-mono">-{formatPaise(sale.discount_paise)}</span>
                </div>
              )}
              {sale.tax_paise > 0 && (
                <div className="flex justify-between text-slate-400">
                  <span>Total Tax</span>
                  <span className="font-mono text-slate-200">{formatPaise(sale.tax_paise)}</span>
                </div>
              )}
              <div className="pt-2 border-t border-slate-800 flex justify-between items-baseline">
                <span className="text-sm font-bold text-slate-100">Final Bill Total</span>
                <span className="text-lg font-mono font-bold text-emerald-400">
                  {formatPaise(sale.total_paise)}
                </span>
              </div>
              <div className="flex justify-between text-[11px] text-slate-500 pt-1">
                <span>Amount Paid ({sale.payment_method})</span>
                <span className="font-mono text-slate-400">{formatPaise(sale.paid_paise)}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Hidden Receipt Component for browser printing */}
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

      {/* Cancellation Reason Modal */}
      {cancelModalOpen && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-2xl">
            <div className="flex items-center gap-3 text-rose-400">
              <AlertTriangle className="w-6 h-6 shrink-0" />
              <h3 className="font-bold text-sm text-slate-100">Cancel & Return Sale</h3>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">
              This will mark Bill #{sale.invoice_number} as CANCELLED and immediately restore all item quantities back to inventory.
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
            <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
              <button
                onClick={() => setCancelModalOpen(false)}
                className="px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:bg-slate-800"
              >
                Back
              </button>
              <button
                onClick={() => cancelMutation.mutate({ id: saleId, reason: cancelReason })}
                disabled={cancelMutation.isPending}
                className="px-4 py-1.5 rounded-lg text-xs font-bold bg-rose-500 hover:bg-rose-400 text-white shadow-md shadow-rose-500/20 disabled:opacity-60"
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
