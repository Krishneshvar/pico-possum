import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { History, Eye, Printer, RotateCcw } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import { formatCurrency, formatDate, formatTime, formatDateTime, getCurrencySymbol } from '../../../lib/formatters';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';
import DataTable from '../../../components/common/DataTable';
import DropdownActions from '../../../components/common/DropdownActions';
import ImportCsvButton from '../../../components/common/ImportCsvButton';
import ExportButton from '../../../components/common/ExportButton';
import RefreshButton from '../../../components/common/RefreshButton';
import { exportToCsv, exportToXlsx, escapeCsv } from '../../../lib/exportUtils';
import { useWorkspaceStore } from '../../../stores/workspaceStore';
import { strings } from '../../../lib/strings';

export default function BillsHistoryView() {
  const queryClient = useQueryClient();
  const { openOrFocus } = useWorkspaceStore();
  const [reprintSale, setReprintSale] = useState(null);
  const [filteredSales, setFilteredSales] = useState(null);

  const { data: salesData, isLoading, refetch } = useQuery({
    queryKey: ['sales'],
    queryFn: () => tauriApi.getSales(500, 0, null),
  });
  const sales = useMemo(() => salesData ?? [], [salesData]);

  const { data: billSettings } = useQuery({
    queryKey: ['billSettings'],
    queryFn: () => tauriApi.getBillSettings(),
  });
  const { data: generalSettings } = useQuery({
    queryKey: ['generalSettings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });
  const currencySymbol = generalSettings?.currencySymbol || getCurrencySymbol();

  const invalidateSalesAndAnalytics = () => {
    queryClient.invalidateQueries({ queryKey: ['sales'] });
    queryClient.invalidateQueries({ queryKey: ['saleDetails'] });
    queryClient.invalidateQueries({ queryKey: ['products'] });
    queryClient.invalidateQueries({ queryKey: ['stockMovements'] });
    queryClient.invalidateQueries({ queryKey: ['productFlow'] });
    queryClient.invalidateQueries({ queryKey: ['salesReportSummary'] });
    queryClient.invalidateQueries({ queryKey: ['topProducts'] });
    queryClient.invalidateQueries({ queryKey: ['topCategories'] });
    queryClient.invalidateQueries({ queryKey: ['multiYearSales'] });
    queryClient.invalidateQueries({ queryKey: ['salesBreakdown'] });
    queryClient.invalidateQueries({ queryKey: ['customers'] });
  };

  const cancelMutation = useMutation({
    mutationFn: ({ id, reason }) => tauriApi.cancelSale(id, reason),
    onSuccess: () => {
      invalidateSalesAndAnalytics();
      toast.success(strings.bills.toasts.saleCancelled);
    },
    onError: (err) =>
      toast.error(strings.bills.toasts.cancelFailed(err), {
        title: strings.bills.toasts.cancelFailedTitle,
      }),
  });

  const handleOpenDetail = (row) => {
    const invNo = row.invoiceNumber ?? row.invoice_number;
    openOrFocus(
      strings.bills.history.tabTitle(invNo),
      'bill-detail',
      { saleId: row.id, invoiceNumber: invNo },
      `bill-${row.id}`
    );
  };

  const handleReprint = (details) => {
    setReprintSale(details);
    toast.info(strings.bills.toasts.receiptPreparing);
    setTimeout(() => window.print(), 100);
  };

  const handleCancelSale = async (saleId, invoiceNumber) => {
    const { confirmed, value } = await confirmDialog({
      title: strings.bills.dialogs.cancelInvoice.title(invoiceNumber),
      message: strings.bills.dialogs.cancelInvoice.message(invoiceNumber),
      input: {
        label: strings.bills.dialogs.cancelInvoice.reasonLabel,
        defaultValue: strings.bills.dialogs.cancelInvoice.reasonDefault,
        placeholder: strings.bills.dialogs.cancelInvoice.reasonPlaceholder,
      },
      confirmLabel: strings.bills.dialogs.cancelInvoice.confirm,
      cancelLabel: strings.bills.dialogs.cancelInvoice.cancel,
      tone: 'danger',
      icon: RotateCcw,
    });

    if (confirmed) {
      cancelMutation.mutate({
        id: saleId,
        reason: value || strings.bills.dialogs.cancelInvoice.reasonDefault,
      });
    }
  };

  const getExportData = () => {
    return filteredSales ?? sales;
  };

  const handleExportCsv = async () => {
    const dataToExport = getExportData();
    if (!dataToExport.length) {
      toast.warning(strings.bills.toasts.noBillsToExport);
      return;
    }

    const headers = [
      strings.common.labels.invoiceNumber,
      strings.common.labels.date,
      strings.common.labels.time,
      strings.bills.export.headers.customerName,
      strings.bills.export.headers.customerPhone,
      strings.common.labels.paymentMethod,
      strings.bills.export.headers.subtotal(currencySymbol),
      strings.bills.export.headers.discount(currencySymbol),
      strings.bills.export.headers.tax(currencySymbol),
      strings.bills.export.headers.netTotal(currencySymbol),
      strings.common.labels.status,
    ];

    const rows = dataToExport.map((s) => {
      const invNo = s.invoiceNumber ?? s.invoice_number ?? '';
      const createdAt = s.createdAt ?? s.created_at;
      const custName = s.customerName ?? s.customer_name ?? strings.bills.labels.walkInCustomer;
      const custPhone = s.customerPhone ?? s.customer_phone ?? '';
      const pm = s.paymentMethod ?? s.payment_method ?? '';
      const subtotal = s.subtotalCents ?? s.subtotal_cents ?? 0;
      const discount = s.discountCents ?? s.discount_cents ?? 0;
      const tax = s.taxCents ?? s.tax_cents ?? 0;
      const total = s.totalCents ?? s.total_cents ?? 0;
      return [
        escapeCsv(invNo),
        escapeCsv(formatDate(createdAt)),
        escapeCsv(formatTime(createdAt)),
        escapeCsv(custName),
        escapeCsv(custPhone),
        escapeCsv(pm),
        escapeCsv((subtotal / 100).toFixed(2)),
        escapeCsv((discount / 100).toFixed(2)),
        escapeCsv((tax / 100).toFixed(2)),
        escapeCsv((total / 100).toFixed(2)),
        escapeCsv(s.status || ''),
      ];
    });

    const dateStr = new Date().toISOString().split('T')[0];
    await exportToCsv({
      filename: `bill-history-${dateStr}.csv`,
      headers,
      rows,
      count: dataToExport.length,
      entityName: strings.bills.export.entityName,
    });
  };

  const handleExportXlsx = async () => {
    const dataToExport = getExportData();
    if (!dataToExport.length) {
      toast.warning(strings.bills.toasts.noBillsToExport);
      return;
    }

    const headers = [
      strings.common.labels.invoiceNumber,
      strings.common.labels.date,
      strings.common.labels.time,
      strings.bills.export.headers.customerName,
      strings.bills.export.headers.customerPhone,
      strings.common.labels.paymentMethod,
      strings.bills.export.headers.subtotal(currencySymbol),
      strings.bills.export.headers.discount(currencySymbol),
      strings.bills.export.headers.tax(currencySymbol),
      strings.bills.export.headers.netTotal(currencySymbol),
      strings.common.labels.status,
    ];

    const rows = dataToExport.map((s) => {
      const invNo = s.invoiceNumber ?? s.invoice_number ?? '';
      const createdAt = s.createdAt ?? s.created_at;
      const custName = s.customerName ?? s.customer_name ?? strings.bills.labels.walkInCustomer;
      const custPhone = s.customerPhone ?? s.customer_phone ?? '';
      const pm = s.paymentMethod ?? s.payment_method ?? '';
      const subtotal = s.subtotalCents ?? s.subtotal_cents ?? 0;
      const discount = s.discountCents ?? s.discount_cents ?? 0;
      const tax = s.taxCents ?? s.tax_cents ?? 0;
      const total = s.totalCents ?? s.total_cents ?? 0;
      return [
        invNo,
        formatDate(createdAt),
        formatTime(createdAt),
        custName,
        custPhone,
        pm,
        parseFloat((subtotal / 100).toFixed(2)),
        parseFloat((discount / 100).toFixed(2)),
        parseFloat((tax / 100).toFixed(2)),
        parseFloat((total / 100).toFixed(2)),
        s.status || '',
      ];
    });

    const dateStr = new Date().toISOString().split('T')[0];
    await exportToXlsx({
      filename: `bill-history-${dateStr}.xlsx`,
      sheetName: strings.bills.export.sheetName,
      headers,
      rows,
      count: dataToExport.length,
      entityName: strings.bills.export.entityName,
    });
  };

  const columns = [
    {
      key: 'invoiceNumber',
      label: strings.common.labels.invoiceNumber,
      className: 'font-mono text-brand font-semibold text-xs',
      render: (r) => {
        const invNo = r.invoiceNumber ?? r.invoice_number;
        return (
          <span
            onClick={(e) => {
              e.stopPropagation();
              handleOpenDetail(r);
            }}
            className="hover:underline cursor-pointer"
          >
            {invNo}
          </span>
        );
      },
    },
    {
      key: 'createdAt',
      label: strings.common.labels.dateTime,
      className: 'text-text-muted text-xs whitespace-nowrap',
      render: (r) => formatDateTime(r.createdAt ?? r.created_at),
    },
    {
      key: 'customerName',
      label: strings.common.labels.customer,
      className: 'font-medium text-text-primary text-xs',
      render: (r) => {
        const custName = r.customerName ?? r.customer_name ?? strings.bills.labels.walkInCustomer;
        const custPhone = r.customerPhone ?? r.customer_phone;
        return (
          <div>
            <span>{custName}</span>
            {custPhone && (
              <div className="text-[10px] text-text-muted font-mono">{custPhone}</div>
            )}
          </div>
        );
      },
    },
    {
      key: 'paymentMethod',
      label: strings.bills.columns.payment,
      headerClassName: 'text-center',
      className: 'text-center text-xs',
      render: (r) => {
        const pm = r.paymentMethod ?? r.payment_method;
        return (
          <span className="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-surface-secondary text-text-secondary border border-border-default uppercase">
            {pm ? pm.replace(/_/g, ' ') : 'CASH'}
          </span>
        );
      },
    },
    {
      key: 'totalCents',
      label: strings.bills.columns.totalAmount,
      headerClassName: 'text-right',
      className: 'text-right financial-value font-bold text-text-primary text-xs',
      render: (r) => formatCurrency(r.totalCents ?? r.total_cents),
    },
    {
      key: 'status',
      label: strings.common.labels.status,
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => {
        const isCancelled = r.status === 'CANCELLED';
        const isLegacy = r.status === 'LEGACY';
        return (
          <span
            className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wider border ${
              isCancelled
                ? 'bg-danger-bg text-danger border-danger/30'
                : isLegacy
                ? 'bg-purple-500/10 text-purple-700 dark:text-purple-300 border-purple-500/30'
                : 'bg-success-bg text-success border-success/30'
            }`}
          >
            {isCancelled
              ? strings.bills.status.cancelled
              : isLegacy
              ? strings.bills.status.legacy
              : strings.bills.status.paid}
          </span>
        );
      },
    },
  ];

  const statusCounts = useMemo(() => {
    let completed = 0;
    let cancelled = 0;
    let legacy = 0;
    for (const s of sales) {
      if (s.status === 'CANCELLED') cancelled++;
      else if (s.status === 'LEGACY') legacy++;
      else completed++;
    }
    return { completed, cancelled, legacy };
  }, [sales]);

  const pmCounts = useMemo(() => {
    const counts = { CASH: 0, UPI: 0, CARD: 0, GIFT_CARD: 0 };
    for (const s of sales) {
      const rawPm = s.paymentMethod ?? s.payment_method;
      const pm = (rawPm || 'CASH').toUpperCase().replace(/\s+/g, '_');
      if (counts[pm] !== undefined) {
        counts[pm]++;
      } else if (pm.includes('GIFT') || pm === 'GC') {
        counts.GIFT_CARD++;
      } else if (pm.includes('CARD') || pm === 'CP') {
        counts.CARD++;
      } else if (pm.includes('UPI') || pm === 'UP') {
        counts.UPI++;
      } else {
        counts.CASH++;
      }
    }
    return counts;
  }, [sales]);

  const filters = useMemo(
    () => [
      {
        type: 'multiselect',
        key: 'status',
        label: strings.common.labels.status,
        options: [
          {
            value: 'COMPLETED',
            label: strings.bills.filters.statusOptions.paid(statusCounts.completed),
          },
          {
            value: 'LEGACY',
            label: strings.bills.filters.statusOptions.legacy(statusCounts.legacy),
          },
          {
            value: 'CANCELLED',
            label: strings.bills.filters.statusOptions.cancelled(statusCounts.cancelled),
          },
        ],
        filterFn: (row, vals) => {
          const rowStatus =
            row.status === 'CANCELLED'
              ? 'CANCELLED'
              : row.status === 'LEGACY'
              ? 'LEGACY'
              : 'COMPLETED';
          return vals.includes(rowStatus);
        },
      },
      {
        type: 'multiselect',
        key: 'paymentMethod',
        label: strings.common.labels.paymentMethod,
        options: [
          { value: 'CASH', label: strings.bills.filters.paymentOptions.cash(pmCounts.CASH) },
          { value: 'UPI', label: strings.bills.filters.paymentOptions.upi(pmCounts.UPI) },
          { value: 'CARD', label: strings.bills.filters.paymentOptions.card(pmCounts.CARD) },
          {
            value: 'GIFT_CARD',
            label: strings.bills.filters.paymentOptions.giftCard(pmCounts.GIFT_CARD),
          },
        ],
        filterFn: (row, vals) => {
          const rawPm = row.paymentMethod ?? row.payment_method;
          const pm = (rawPm || 'CASH').toUpperCase().replace(/\s+/g, '_');
          const normalized = pm.includes('GIFT') || pm === 'GC'
            ? 'GIFT_CARD'
            : pm.includes('CARD') || pm === 'CP'
            ? 'CARD'
            : pm.includes('UPI') || pm === 'UP'
            ? 'UPI'
            : 'CASH';
          return vals.includes(normalized);
        },
      },
      {
        type: 'daterange',
        fromKey: 'createdAt',
        toKey: 'createdAt',
        label: strings.common.datePicker.dateRange,
      },
      {
        type: 'range',
        key: 'totalCents',
        label: strings.common.labels.amount,
        unit: currencySymbol,
        transform: (v) => (v != null ? Number(v) / 100 : 0),
        filterFn: (row, range) => {
          const val = (row.totalCents ?? row.total_cents ?? 0) / 100;
          const hasMin = range.min !== '' && range.min != null && !isNaN(Number(range.min));
          const hasMax = range.max !== '' && range.max != null && !isNaN(Number(range.max));
          if (hasMin && val < Number(range.min)) return false;
          if (hasMax && val > Number(range.max)) return false;
          return true;
        },
      },
    ],
    [statusCounts, pmCounts, currencySymbol]
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
            <h2 className="text-sm font-bold text-text-primary">{strings.bills.history.title}</h2>
            <p className="text-[11px] text-text-muted">
              {strings.bills.history.subtitle}
            </p>
          </div>
        </div>

        {/* Import & Export Actions */}
        <div className="flex items-center gap-2">
          <ImportCsvButton
            entity="bills"
            onComplete={() => {
              invalidateSalesAndAnalytics();
            }}
          />

          <ExportButton
            onExportCsv={handleExportCsv}
            onExportXlsx={handleExportXlsx}
          />

          <RefreshButton
            onClick={() => refetch()}
            isLoading={isLoading}
          />
        </div>
      </div>

      {/* ── DataTable with Filters & Actions ── */}
      <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
        <DataTable
          columns={columns}
          data={sales}
          keyFn={(r) => r.id}
          searchKeys={['invoiceNumber', 'customerName', 'customerPhone']}
          filters={filters}
          persistKey="billHistory"
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
                        onClick: () => handleCancelSale(row.id, row.invoiceNumber ?? row.invoice_number),
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
