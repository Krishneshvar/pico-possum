import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { History, Eye, Printer, RotateCcw, FileText, FileSpreadsheet, Download, X } from 'lucide-react';
import * as XLSX from 'xlsx';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import { formatPaise, formatDate, formatTime, formatDateTime, getCurrencySymbol } from '../../../lib/formatters';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';
import DataTable from '../../../components/common/DataTable';
import DropdownActions from '../../../components/common/DropdownActions';
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
      toast.success('Sale marked as cancelled and inventory restored');
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

  const handleReprint = (details) => {
    setReprintSale(details);
    toast.info('Preparing receipt for printing');
    setTimeout(() => window.print(), 100);
  };

  const handleCancelSale = async (saleId, invoiceNumber) => {
    const { confirmed, value } = await confirmDialog({
      title: `Cancel Invoice ${invoiceNumber || ''}?`,
      message: `Are you sure you want to cancel invoice #${invoiceNumber}? This will mark the transaction as CANCELLED and restore the items to inventory.`,
      input: {
        label: 'Reason for Cancellation / Return',
        defaultValue: 'Customer returned items / cancelled',
        placeholder: 'Reason for cancellation',
      },
      confirmLabel: 'Cancel Invoice',
      cancelLabel: 'Keep Active',
      tone: 'danger',
      icon: RotateCcw,
    });

    if (confirmed) {
      cancelMutation.mutate({
        id: saleId,
        reason: value || 'Customer returned items / cancelled',
      });
    }
  };

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
      'Payment Method',
      'Subtotal (Rs)',
      'Discount (Rs)',
      'Tax (Rs)',
      'Net Total (Rs)',
      'Status',
    ];

    const rows = dataToExport.map((s) => [
      escapeCsv(s.invoice_number || ''),
      escapeCsv(formatDate(s.created_at)),
      escapeCsv(formatTime(s.created_at)),
      escapeCsv(s.customer_name || 'Walk-in Customer'),
      escapeCsv(s.customer_phone || ''),
      escapeCsv(s.payment_method || ''),
      escapeCsv((s.subtotal_paise / 100).toFixed(2)),
      escapeCsv((s.discount_paise / 100).toFixed(2)),
      escapeCsv((s.tax_paise / 100).toFixed(2)),
      escapeCsv((s.total_paise / 100).toFixed(2)),
      escapeCsv(s.status || ''),
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
      'Payment Method',
      'Subtotal (Rs)',
      'Discount (Rs)',
      'Tax (Rs)',
      'Net Total (Rs)',
      'Status',
    ];

    const rows = dataToExport.map((s) => [
      s.invoice_number || '',
      formatDate(s.created_at),
      formatTime(s.created_at),
      s.customer_name || 'Walk-in Customer',
      s.customer_phone || '',
      s.payment_method || '',
      parseFloat((s.subtotal_paise / 100).toFixed(2)),
      parseFloat((s.discount_paise / 100).toFixed(2)),
      parseFloat((s.tax_paise / 100).toFixed(2)),
      parseFloat((s.total_paise / 100).toFixed(2)),
      s.status || '',
    ]);

    const worksheet = XLSX.utils.aoa_to_sheet([headers, ...rows]);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Bills');
    const dateStr = new Date().toISOString().split('T')[0];
    XLSX.writeFile(workbook, `bill_history_${dateStr}.xlsx`);
    toast.success(`Exported ${dataToExport.length} bills to Excel`);
  };

  const columns = [
    {
      key: 'invoice_number',
      label: 'Invoice #',
      className: 'font-mono text-brand font-semibold text-xs',
      render: (r) => (
        <span
          onClick={(e) => {
            e.stopPropagation();
            handleOpenDetail(r);
          }}
          className="hover:underline cursor-pointer"
        >
          {r.invoice_number}
        </span>
      ),
    },
    {
      key: 'created_at',
      label: 'Date & Time',
      className: 'text-text-muted text-xs whitespace-nowrap',
      render: (r) => formatDateTime(r.created_at),
    },
    {
      key: 'customer_name',
      label: 'Customer',
      className: 'font-medium text-text-primary text-xs',
      render: (r) => (
        <div>
          <span>{r.customer_name || 'Walk-in Customer'}</span>
          {r.customer_phone && (
            <div className="text-[10px] text-text-muted font-mono">{r.customer_phone}</div>
          )}
        </div>
      ),
    },
    {
      key: 'payment_method',
      label: 'Payment',
      headerClassName: 'text-center',
      className: 'text-center text-xs',
      render: (r) => (
        <span className="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-surface-secondary text-text-secondary border border-border-default uppercase">
          {r.payment_method || 'CASH'}
        </span>
      ),
    },
    {
      key: 'total_paise',
      label: 'Total Amount',
      headerClassName: 'text-right',
      className: 'text-right financial-value font-bold text-text-primary text-xs',
      render: (r) => formatPaise(r.total_paise),
    },
    {
      key: 'status',
      label: 'Status',
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => {
        const isCancelled = r.status === 'CANCELLED';
        return (
          <span
            className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wider border ${
              isCancelled
                ? 'bg-danger-bg text-danger border-danger/30'
                : 'bg-success-bg text-success border-success/30'
            }`}
          >
            {isCancelled ? 'Cancelled' : 'Paid'}
          </span>
        );
      },
    },
  ];

  const statusCounts = useMemo(() => {
    let completed = 0;
    let cancelled = 0;
    for (const s of sales) {
      if (s.status === 'CANCELLED') cancelled++;
      else completed++;
    }
    return { completed, cancelled };
  }, [sales]);

  const pmCounts = useMemo(() => {
    const counts = { CASH: 0, UPI: 0, CARD: 0, OTHER: 0 };
    for (const s of sales) {
      const pm = (s.payment_method || 'CASH').toUpperCase();
      if (counts[pm] !== undefined) {
        counts[pm]++;
      } else {
        counts.OTHER++;
      }
    }
    return counts;
  }, [sales]);

  const filters = useMemo(
    () => [
      {
        type: 'multiselect',
        key: 'status',
        label: 'Status',
        options: [
          { value: 'COMPLETED', label: `Paid (${statusCounts.completed})` },
          { value: 'CANCELLED', label: `Cancelled (${statusCounts.cancelled})` },
        ],
        filterFn: (row, vals) => {
          const isCancelled = row.status === 'CANCELLED';
          const rowStatus = isCancelled ? 'CANCELLED' : 'COMPLETED';
          return vals.includes(rowStatus);
        },
      },
      {
        type: 'multiselect',
        key: 'payment_method',
        label: 'Payment Method',
        options: [
          { value: 'CASH', label: `Cash (${pmCounts.CASH})` },
          { value: 'UPI', label: `UPI / QR (${pmCounts.UPI})` },
          { value: 'CARD', label: `Card (${pmCounts.CARD})` },
          { value: 'OTHER', label: `Other (${pmCounts.OTHER})` },
        ],
        filterFn: (row, vals) => {
          const pm = (row.payment_method || 'CASH').toUpperCase();
          const normalized = ['CASH', 'UPI', 'CARD'].includes(pm) ? pm : 'OTHER';
          return vals.includes(normalized);
        },
      },
      {
        type: 'daterange',
        fromKey: 'created_at',
        toKey: 'created_at',
        label: 'Date Range',
      },
    ],
    [statusCounts, pmCounts]
  );

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas overflow-hidden">
      {/* ── Page Header with Export Controls near Title ── */}
      <div className="h-14 px-5 border-b border-border-default bg-surface flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-brand-subtle text-brand flex items-center justify-center">
            <History className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-text-primary">Bill History</h2>
            <p className="text-[11px] text-text-muted">
              Search, reprint receipts, inspect audits, and manage past sales
            </p>
          </div>
        </div>

        {/* Export Actions */}
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={handleExportCsv}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-border-default bg-surface hover:bg-surface-secondary text-text-secondary hover:text-text-primary text-xs font-medium transition-colors cursor-pointer shadow-2xs"
            title="Export filtered bills to CSV"
          >
            <FileText className="w-3.5 h-3.5 text-text-muted" />
            <span>CSV</span>
          </button>
          <button
            type="button"
            onClick={handleExportXlsx}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-border-default bg-surface hover:bg-surface-secondary text-text-secondary hover:text-text-primary text-xs font-medium transition-colors cursor-pointer shadow-2xs"
            title="Export filtered bills to Excel"
          >
            <FileSpreadsheet className="w-3.5 h-3.5 text-text-muted" />
            <span>Excel</span>
          </button>
        </div>
      </div>

      {/* ── DataTable with Filters & Actions ── */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          data={sales}
          keyFn={(r) => r.id}
          searchKeys={['invoice_number', 'customer_name', 'customer_phone']}
          filters={filters}
          persistKey="bill_history"
          isLoading={isLoading}
          onRowDoubleClick={handleOpenDetail}
          onFilteredChange={setFilteredSales}
          actions={(row) => (
            <DropdownActions
              items={[
                {
                  label: 'View Details',
                  icon: <Eye className="w-3.5 h-3.5" />,
                  onClick: () => handleOpenDetail(row),
                },
                {
                  label: 'Reprint Receipt',
                  icon: <Printer className="w-3.5 h-3.5" />,
                  onClick: async () => {
                    try {
                      const details = await tauriApi.getSaleDetails(row.id);
                      handleReprint(details);
                    } catch (err) {
                      toast.error(`Failed to load receipt: ${err}`);
                    }
                  },
                },
                ...(row.status !== 'CANCELLED'
                  ? [
                      'divider',
                      {
                        label: 'Cancel Invoice & Return Stock',
                        icon: <RotateCcw className="w-3.5 h-3.5" />,
                        danger: true,
                        onClick: () => handleCancelSale(row.id, row.invoice_number),
                      },
                    ]
                  : []),
              ]}
            />
          )}
        />
      </div>

      {/* Hidden printable receipt container */}
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
