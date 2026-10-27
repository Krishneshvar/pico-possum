import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { History, Eye, Pencil, Printer, RotateCcw, FileText, FileSpreadsheet } from 'lucide-react';
import * as XLSX from 'xlsx';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import { formatPaise, formatDate, formatTime, formatDateTime, getCurrencySymbol } from '../../../lib/formatters';
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
  const [filteredSales, setFilteredSales] = useState(null);

  const { data: salesData, isLoading } = useQuery({
    queryKey: ['sales'],
    queryFn: () => tauriApi.getSales(500, 0, null),
  });
  const sales = useMemo(() => salesData ?? [], [salesData]);

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

  // ── CSV & XLSX Export Handlers ──────────────────────────────────────────────
  const getExportData = () => {
    return filteredSales ?? sales;
  };

  const handleExportCsv = () => {
    const dataToExport = getExportData();
    if (!dataToExport.length) {
      toast.warning('No bills to export');
      return;
    }

    const escapeCsv = (val) => {
      const str = String(val ?? '');
      if (str.includes(',') || str.includes('"') || str.includes('\n') || str.includes('\r')) {
        return `"${str.replace(/"/g, '""')}"`;
      }
      return str;
    };

    const headers = [
      'Invoice #',
      'Date',
      'Time',
      'Customer Name',
      'Customer Phone',
      'Biller',
      'Payment Method',
      'Subtotal (Rs)',
      'Discount (Rs)',
      'Tax (Rs)',
      'Net Total (Rs)',
      'Paid (Rs)',
      'Status',
      'Notes',
    ];

    const rows = dataToExport.map((s) => [
      escapeCsv(s.invoice_number || ''),
      escapeCsv(formatDate(s.created_at)),
      escapeCsv(formatTime(s.created_at)),
      escapeCsv(s.customer_name || 'Walk-in Customer'),
      escapeCsv(s.customer_phone || ''),
      escapeCsv(s.biller_name || ''),
      escapeCsv(s.payment_method || ''),
      escapeCsv((s.subtotal_paise / 100).toFixed(2)),
      escapeCsv((s.discount_paise / 100).toFixed(2)),
      escapeCsv((s.tax_paise / 100).toFixed(2)),
      escapeCsv((s.total_paise / 100).toFixed(2)),
      escapeCsv((s.paid_paise / 100).toFixed(2)),
      escapeCsv(s.status || ''),
      escapeCsv(s.notes || ''),
    ]);

    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    const dateStr = new Date().toISOString().split('T')[0];
    link.setAttribute('download', `bill_history_${dateStr}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    toast.success(`Exported ${dataToExport.length} bills to CSV`);
  };

  const handleExportXlsx = () => {
    const dataToExport = getExportData();
    if (!dataToExport.length) {
      toast.warning('No bills to export');
      return;
    }

    const headers = [
      'Invoice #',
      'Date',
      'Time',
      'Customer Name',
      'Customer Phone',
      'Biller',
      'Payment Method',
      'Subtotal (Rs)',
      'Discount (Rs)',
      'Tax (Rs)',
      'Net Total (Rs)',
      'Paid (Rs)',
      'Status',
      'Notes',
    ];

    const rows = dataToExport.map((s) => [
      s.invoice_number || '',
      formatDate(s.created_at),
      formatTime(s.created_at),
      s.customer_name || 'Walk-in Customer',
      s.customer_phone || '',
      s.biller_name || '',
      s.payment_method || '',
      Number((s.subtotal_paise / 100).toFixed(2)),
      Number((s.discount_paise / 100).toFixed(2)),
      Number((s.tax_paise / 100).toFixed(2)),
      Number((s.total_paise / 100).toFixed(2)),
      Number((s.paid_paise / 100).toFixed(2)),
      s.status || '',
      s.notes || '',
    ]);

    const ws = XLSX.utils.aoa_to_sheet([headers, ...rows]);
    ws['!cols'] = [
      { wch: 16 }, // Invoice
      { wch: 12 }, // Date
      { wch: 10 }, // Time
      { wch: 22 }, // Customer Name
      { wch: 15 }, // Customer Phone
      { wch: 14 }, // Biller
      { wch: 14 }, // Payment Method
      { wch: 14 }, // Subtotal
      { wch: 14 }, // Discount
      { wch: 12 }, // Tax
      { wch: 14 }, // Net Total
      { wch: 14 }, // Paid
      { wch: 12 }, // Status
      { wch: 25 }, // Notes
    ];

    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Bills');
    const dateStr = new Date().toISOString().split('T')[0];
    XLSX.writeFile(wb, `bill_history_${dateStr}.xlsx`);
    toast.success(`Exported ${dataToExport.length} bills to Excel (XLSX)`);
  };

  const columns = useMemo(() => [
    {
      key: 'invoice_number',
      label: 'Invoice #',
      className: 'font-mono font-bold text-slate-100',
    },
    {
      key: 'created_at',
      label: 'Date & Time',
      className: 'font-mono text-slate-400 text-[11px]',
      render: (r) => formatDateTime(r.created_at),
    },
    {
      key: 'customer_name',
      label: 'Customer',
      className: 'text-slate-300',
      render: (r) => (
        <div>
          <div>{r.customer_name || <span className="text-slate-500 italic">Walk-in</span>}</div>
          {r.customer_phone && <div className="text-[11px] text-slate-500 font-mono">{r.customer_phone}</div>}
        </div>
      ),
    },
    {
      key: 'payment_method',
      label: 'Payment',
      render: (r) => (
        <span className="text-[11px] font-semibold px-2 py-0.5 rounded bg-slate-800 text-slate-300">
          {r.payment_method}
        </span>
      ),
    },
    {
      key: 'total_paise',
      label: 'Total',
      headerClassName: 'text-right',
      className: 'text-right font-mono font-bold text-slate-100',
      render: (r) => formatPaise(r.total_paise),
    },
    {
      key: 'status',
      label: 'Status',
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => (
        <span
          className={`inline-flex px-2 py-0.5 rounded-full text-[10px] font-bold border ${
            r.status === 'CANCELLED'
              ? 'bg-rose-500/15 text-rose-400 border-rose-500/30'
              : 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30'
          }`}
        >
          {r.status}
        </span>
      ),
    },
  ], [currencySymbol]);

  const filters = useMemo(() => [
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
  ], [currencySymbol]);

  const searchKeys = useMemo(
    () => ['invoice_number', 'customer_name', 'customer_phone', 'payment_method'],
    []
  );

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
          {/* Export CSV */}
          <button
            onClick={handleExportCsv}
            disabled={!sales.length}
            className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs flex items-center gap-1.5 border border-slate-700/60 transition-all disabled:opacity-40 cursor-pointer"
            title="Export bills as CSV"
          >
            <FileText className="w-3.5 h-3.5 text-sky-400" />
            <span>CSV</span>
          </button>

          {/* Export XLSX */}
          <button
            onClick={handleExportXlsx}
            disabled={!sales.length}
            className="px-3 py-1.5 rounded-lg bg-emerald-600/90 hover:bg-emerald-500 text-slate-950 font-bold text-xs flex items-center gap-1.5 border border-emerald-500/40 shadow-sm transition-all disabled:opacity-40 cursor-pointer"
            title="Export bills as Excel XLSX"
          >
            <FileSpreadsheet className="w-3.5 h-3.5" />
            <span>Excel (XLSX)</span>
          </button>

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
          searchKeys={searchKeys}
          filters={filters}
          isLoading={isLoading}
          emptyMessage="No sales recorded."
          onFilteredChange={setFilteredSales}
          onRowDoubleClick={handleOpenDetail}
          actions={(row) => (
            <DropdownActions
              items={[
                {
                  label: 'View Details',
                  icon: <Eye className="w-3.5 h-3.5" />,
                  onClick: () => handleOpenDetail(row),
                },
                ...(row.status !== 'CANCELLED'
                  ? [
                      {
                        label: 'Edit Bill',
                        icon: <Pencil className="w-3.5 h-3.5" />,
                        onClick: () => handleEditBill(row),
                      },
                    ]
                  : []),
                {
                  label: 'Quick Reprint',
                  icon: <Printer className="w-3.5 h-3.5" />,
                  onClick: () => {
                    setTimeout(() => {
                      tauriApi
                        .getSaleDetails(row.id)
                        .then((d) => handleReprint(d))
                        .catch((err) =>
                          toast.error(`Could not load receipt: ${err}`, { title: 'Reprint failed' })
                        );
                    }, 100);
                  },
                },
                ...(row.status !== 'CANCELLED'
                  ? [
                      'divider',
                      {
                        label: 'Cancel & Return',
                        icon: <RotateCcw className="w-3.5 h-3.5" />,
                        danger: true,
                        onClick: () => {
                          handleCancelSale(row.id, row.invoice_number);
                        },
                      },
                    ]
                  : []),
              ]}
            />
          )}
        />
      </div>

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
    </div>
  );
}
