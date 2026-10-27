import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { History, Eye, Pencil, Printer, RotateCcw } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import { formatPaise, formatDateTime, getCurrencySymbol } from '../../../lib/formatters';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';
import DataTable from '../../../components/common/DataTable';
import DropdownActions from '../../../components/common/DropdownActions';
import ImportCsvButton from '../../../components/common/ImportCsvButton';
import { useWorkspaceStore } from '../../../stores/workspaceStore';


export default function BillsHistoryView() {
  const queryClient = useQueryClient();
  const { openOrFocus } = useWorkspaceStore();
  const currencySymbol = getCurrencySymbol();
  const [reprintSale, setReprintSale] = useState(null);

  const { data: sales = [], isLoading } = useQuery({
    queryKey: ['sales'],
    queryFn: () => tauriApi.getSales(500, 0, null),
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
      toast.success('Sale cancelled and all items returned to stock');
    },
    onError: (err) => toast.error(`Failed to cancel sale: ${err}`, { title: 'Cancellation failed' }),
  });

  const handleOpenDetail = (row) => {
    openOrFocus(
      `Bill #${row.invoice_number}`,
      'bill-detail',
      { saleId: row.id, invoiceNumber: row.invoice_number },
      `bill-${row.id}`
    );
  };

  const handleEditBill = (row) => {
    openOrFocus(
      `Bill #${row.invoice_number}`,
      'bill-detail',
      {
        saleId: row.id,
        invoiceNumber: row.invoice_number,
        editRequest: Date.now(),
      },
      `bill-${row.id}`
    );
  };

  const handleReprint = (details) => {
    setReprintSale(details);
    toast.info('Preparing receipt for printing');
    setTimeout(() => window.print(), 100);
  };

  const handleCancelSale = async (saleId, invoiceNumber) => {
    const { confirmed, value } = await confirmDialog({
      title: 'Cancel & Return Sale',
      message: invoiceNumber
        ? `This will mark Bill #${invoiceNumber} as CANCELLED and automatically restore all items back into inventory.`
        : 'This will mark the sale as CANCELLED and automatically restore all items back into inventory.',
      input: {
        label: 'Reason for Return',
        defaultValue: 'Customer return / refund',
        placeholder: 'Reason for return',
      },
      confirmLabel: 'Confirm Cancellation',
      cancelLabel: 'Back',
      icon: RotateCcw,
    });

    if (confirmed) {
      cancelMutation.mutate({
        id: saleId,
        reason: value || 'Customer return / refund',
      });
    }
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
      <div className="h-14 px-5 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <History className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-100">Bill History & Sales Ledger</h2>
            <p className="text-[11px] text-slate-500">View past sales, reprint receipts, and process returns</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <ImportCsvButton
            entity="bills"
            label="Import Legacy CSV"
            onComplete={() => {
              queryClient.invalidateQueries({ queryKey: ['sales'] });
              queryClient.invalidateQueries({ queryKey: ['sales_report_summary'] });
              queryClient.invalidateQueries({ queryKey: ['sales_breakdown'] });
            }}
          />
        </div>
      </div>

      {/* Data Table */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          persistKey="bills"
          data={sales}
          keyFn={(r) => r.id}
          searchKeys={['invoice_number', 'customer_name', 'customer_phone', 'payment_method']}
          filters={[
            {
              type: 'multiselect',
              key: 'status',
              label: 'Status',
              options: [
                { value: 'PAID', label: 'Paid' },
                { value: 'CANCELLED', label: 'Cancelled' },
              ],
            },
            {
              type: 'multiselect',
              key: 'payment_method',
              label: 'Payment Method',
              options: [
                { value: 'CASH', label: 'Cash' },
                { value: 'UPI', label: 'UPI' },
                { value: 'CARD', label: 'Card' },
              ],
            },
            {
              type: 'daterange',
              fromKey: 'created_at',
              toKey: 'created_at',
              label: 'Date',
            },
            {
              type: 'range',
              key: 'total_paise',
              label: 'Total',
              unit: currencySymbol,
              transform: (v) => Math.round(parseFloat(v) * 100),
            },
          ]}
          isLoading={isLoading}
          emptyMessage="No sales recorded."
          onRowDoubleClick={handleOpenDetail}
          actions={(row) => (
            <DropdownActions items={[
              { label: 'View Details', icon: <Eye className="w-3.5 h-3.5" />, onClick: () => handleOpenDetail(row) },
              ...(row.status !== 'CANCELLED' ? [
                { label: 'Edit Bill', icon: <Pencil className="w-3.5 h-3.5" />, onClick: () => handleEditBill(row) },
              ] : []),
              { label: 'Quick Reprint', icon: <Printer className="w-3.5 h-3.5" />, onClick: () => {
                  setTimeout(() => {
                    tauriApi
                      .getSaleDetails(row.id)
                      .then((d) => handleReprint(d))
                      .catch((err) =>
                        toast.error(`Could not load receipt: ${err}`, { title: 'Reprint failed' })
                      );
                  }, 100);
                }
              },
              ...(row.status !== 'CANCELLED' ? [
                'divider',
                { label: 'Cancel & Return', icon: <RotateCcw className="w-3.5 h-3.5" />, danger: true, onClick: () => {
                    handleCancelSale(row.id, row.invoice_number);
                  }
                },
              ] : []),
            ]} />
          )}
        />
      </div>

      {reprintSale && (
        <div className="hidden">
          <ReceiptPrinter sale={reprintSale.sale} items={reprintSale.items} billSettings={billSettings} generalSettings={generalSettings} />
        </div>
      )}
    </div>
  );
}
