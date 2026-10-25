import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { History, Eye, Printer, RotateCcw, X, AlertTriangle } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise, formatDateTime } from '../../../lib/formatters';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';
import DataTable from '../../../components/common/DataTable';

export default function BillsHistoryView() {
  const queryClient = useQueryClient();
  const [selectedSaleId, setSelectedSaleId] = useState(null);
  const [reprintSale, setReprintSale] = useState(null);
  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('Customer return / refund');

  const { data: sales = [], isLoading } = useQuery({
    queryKey: ['sales'],
    queryFn: () => tauriApi.getSales(500, 0, null),
  });

  const { data: saleDetails } = useQuery({
    queryKey: ['sale_details', selectedSaleId],
    queryFn: () => tauriApi.getSaleDetails(selectedSaleId),
    enabled: !!selectedSaleId,
  });

  const { data: billSettings } = useQuery({ queryKey: ['bill_settings'], queryFn: () => tauriApi.getBillSettings() });
  const { data: generalSettings } = useQuery({ queryKey: ['general_settings'], queryFn: () => tauriApi.getGeneralSettings() });

  const cancelMutation = useMutation({
    mutationFn: ({ id, reason }) => tauriApi.cancelSale(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sales'] });
      queryClient.invalidateQueries({ queryKey: ['sale_details'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['stock_movements'] });
      setCancelModalOpen(false);
      setSelectedSaleId(null);
    },
    onError: (err) => alert(`Failed to cancel: ${err}`),
  });

  const handleReprint = (details) => {
    setReprintSale(details);
    setTimeout(() => window.print(), 100);
  };

  const columns = [
    {
      key: 'invoice_number', label: 'Invoice #',
      className: 'font-mono font-bold text-slate-100',
    },
    {
      key: 'created_at', label: 'Date & Time',
      className: 'font-mono text-slate-400 text-[11px]',
      render: (r) => formatDateTime(r.created_at),
    },
    {
      key: 'customer_name', label: 'Customer',
      className: 'text-slate-300',
      render: (r) => (
        <div>
          <div>{r.customer_name || <span className="text-slate-500 italic">Walk-in</span>}</div>
          {r.customer_phone && <div className="text-[11px] text-slate-500 font-mono">{r.customer_phone}</div>}
        </div>
      ),
    },
    {
      key: 'payment_method', label: 'Payment',
      render: (r) => (
        <span className="text-[11px] font-semibold px-2 py-0.5 rounded bg-slate-800 text-slate-300">
          {r.payment_method}
        </span>
      ),
    },
    {
      key: 'total_paise', label: 'Total',
      headerClassName: 'text-right',
      className: 'text-right font-mono font-bold text-slate-100',
      render: (r) => formatPaise(r.total_paise),
    },
    {
      key: 'status', label: 'Status',
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => (
        <span className={`inline-flex px-2 py-0.5 rounded-full text-[10px] font-bold border ${
          r.status === 'CANCELLED'
            ? 'bg-rose-500/15 text-rose-400 border-rose-500/30'
            : 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30'
        }`}>
          {r.status}
        </span>
      ),
    },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-14 px-5 border-b border-slate-800/80 bg-slate-900/50 flex items-center flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <History className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-100">Bill History & Sales Ledger</h2>
            <p className="text-[11px] text-slate-500">View past sales, reprint receipts, and process returns</p>
          </div>
        </div>
      </div>

      {/* Data Table */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          data={sales}
          keyFn={(r) => r.id}
          searchKeys={['invoice_number', 'customer_name', 'customer_phone', 'payment_method']}
          filters={[
            {
              key: 'status',
              label: 'All Statuses',
              options: [
                { value: 'PAID', label: 'Paid' },
                { value: 'CANCELLED', label: 'Cancelled' },
              ],
            },
            {
              key: 'payment_method',
              label: 'All Payment Methods',
              options: [
                { value: 'CASH', label: 'Cash' },
                { value: 'UPI', label: 'UPI' },
                { value: 'CARD', label: 'Card' },
              ],
            },
          ]}
          isLoading={isLoading}
          emptyMessage="No sales recorded."
          actions={(row) => (
            <button
              onClick={() => setSelectedSaleId(row.id)}
              className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium inline-flex items-center gap-1.5 transition-colors"
            >
              <Eye className="w-3.5 h-3.5 text-emerald-400" />
              View
            </button>
          )}
        />
      </div>

      {/* Bill Details Modal */}
      {selectedSaleId && saleDetails && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 shadow-2xl space-y-4 max-h-[90vh] flex flex-col">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <div>
                <h3 className="font-bold text-base text-slate-100 flex items-center gap-2">
                  <span>Bill #{saleDetails.sale.invoice_number}</span>
                  <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                    saleDetails.sale.status === 'CANCELLED' ? 'bg-rose-500/20 text-rose-400' : 'bg-emerald-500/20 text-emerald-400'
                  }`}>{saleDetails.sale.status}</span>
                </h3>
                <p className="text-xs text-slate-400 mt-0.5 font-mono">{formatDateTime(saleDetails.sale.created_at)}</p>
              </div>
              <button onClick={() => setSelectedSaleId(null)} className="text-slate-400 hover:text-white"><X className="w-4 h-4" /></button>
            </div>

            <div className="grid grid-cols-2 gap-3 text-xs bg-slate-950/60 p-3 rounded-xl border border-slate-800">
              <div>
                <span className="text-slate-500 block">Customer</span>
                <span className="font-semibold text-slate-200">{saleDetails.sale.customer_name || 'Walk-in Customer'}</span>
                {saleDetails.sale.customer_phone && <span className="text-slate-400 block font-mono">{saleDetails.sale.customer_phone}</span>}
              </div>
              <div>
                <span className="text-slate-500 block">Payment Method</span>
                <span className="font-semibold text-slate-200">{saleDetails.sale.payment_method}</span>
                <span className="text-slate-400 block">Paid: {formatPaise(saleDetails.sale.paid_paise)}</span>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto border border-slate-800 rounded-xl divide-y divide-slate-800/60">
              {saleDetails.items.map((it) => (
                <div key={it.id} className="p-3 text-xs flex justify-between items-center">
                  <div>
                    <div className="font-semibold text-slate-200">{it.product_name}</div>
                    <div className="text-[11px] text-slate-400 font-mono">{it.quantity} × {formatPaise(it.unit_price_paise)}</div>
                  </div>
                  <div className="font-bold text-slate-100 font-mono">{formatPaise(it.total_paise)}</div>
                </div>
              ))}
            </div>

            <div className="space-y-1.5 text-xs text-slate-300 pt-2 border-t border-slate-800">
              <div className="flex justify-between"><span>Subtotal</span><span className="font-mono">{formatPaise(saleDetails.sale.subtotal_paise)}</span></div>
              {saleDetails.sale.discount_paise > 0 && (
                <div className="flex justify-between text-amber-400"><span>Discount</span><span className="font-mono">-{formatPaise(saleDetails.sale.discount_paise)}</span></div>
              )}
              <div className="flex justify-between text-base font-bold text-slate-100 pt-1 border-t border-slate-800">
                <span>Total</span>
                <span className="font-mono text-emerald-400">{formatPaise(saleDetails.sale.total_paise)}</span>
              </div>
            </div>

            <div className="flex items-center justify-between pt-3 border-t border-slate-800">
              {saleDetails.sale.status !== 'CANCELLED' ? (
                <button onClick={() => setCancelModalOpen(true)} className="px-3 py-1.5 rounded-lg text-rose-400 hover:bg-rose-500/10 text-xs font-semibold flex items-center gap-1.5 transition-colors border border-rose-500/20">
                  <RotateCcw className="w-3.5 h-3.5" /> Cancel / Return
                </button>
              ) : <div className="text-xs text-rose-400 font-semibold">Cancelled</div>}
              <button onClick={() => handleReprint(saleDetails)} className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-bold flex items-center gap-2 border border-slate-700 transition-colors">
                <Printer className="w-4 h-4" /> Reprint Receipt
              </button>
            </div>
          </div>
        </div>
      )}

      {reprintSale && (
        <div className="hidden">
          <ReceiptPrinter sale={reprintSale.sale} items={reprintSale.items} billSettings={billSettings} generalSettings={generalSettings} />
        </div>
      )}

      {cancelModalOpen && selectedSaleId && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-2xl">
            <div className="flex items-center gap-3 text-rose-400">
              <AlertTriangle className="w-6 h-6" />
              <h3 className="font-bold text-sm text-slate-100">Cancel & Return Sale</h3>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">This will mark the sale as CANCELLED and automatically restore all items back into inventory.</p>
            <div>
              <label className="text-xs text-slate-400 block mb-1">Reason for Return</label>
              <input type="text" value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-xs text-slate-200 focus:outline-none focus:border-rose-500" />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button onClick={() => setCancelModalOpen(false)} className="px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:bg-slate-800">Back</button>
              <button onClick={() => cancelMutation.mutate({ id: selectedSaleId, reason: cancelReason })} disabled={cancelMutation.isPending} className="px-4 py-1.5 rounded-lg text-xs font-bold bg-rose-500 hover:bg-rose-400 text-white shadow-md shadow-rose-500/20 disabled:opacity-60">
                Confirm Cancellation
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
