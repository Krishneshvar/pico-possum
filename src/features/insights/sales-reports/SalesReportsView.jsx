import { useState, useMemo, useRef, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Calendar,
  RefreshCw,
  SlidersHorizontal,
  Check,
  RotateCcw,
  ChevronDown,
  ChevronUp,
  X,
  Filter,
} from 'lucide-react';
import * as XLSX from 'xlsx';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise, formatDate, getCurrencySymbol } from '../../../lib/formatters';
import { toast } from '../../../stores/toast.store';
import DataTable from '../../../components/common/DataTable';
import Select from '../../../components/common/Select';
import DatePicker, { formatToIso } from '../../../components/common/DatePicker';
import ExportButton from '../../../components/common/ExportButton';
import { exportWithUserLocation, escapeCsv } from '../../../lib/exportUtils';
import { strings } from '../../../lib/strings';

export default function SalesReportsView() {
  const currencySymbol = getCurrencySymbol();
  const todayIso = useMemo(() => formatToIso(new Date()), []);

  const [reportType, setReportType] = useState('today');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [advancedOpen, setAdvancedOpen] = useState(false);

  // Amount filtering
  const [amountCol, setAmountCol] = useState('gross');
  const [minAmount, setMinAmount] = useState('');
  const [maxAmount, setMaxAmount] = useState('');

  // Column visibility
  const defaultColKeys = [
    'period',
    'total_transactions',
    'cash_paise',
    'upi_paise',
    'card_paise',
    'gift_card_paise',
    'total_discount_paise',
    'total_sales_paise',
    'net_sales',
  ];
  const [visibleColKeys, setVisibleColKeys] = useState(defaultColKeys);
  const [colMenuOpen, setColMenuOpen] = useState(false);
  const colMenuRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (colMenuRef.current && !colMenuRef.current.contains(e.target)) {
        setColMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const effectiveFromDate = reportType === 'today' ? (fromDate || todayIso) : (fromDate || null);
  const effectiveToDate = reportType === 'today' ? (toDate || todayIso) : (toDate || null);

  const { data: rawData = [], isLoading, refetch } = useQuery({
    queryKey: ['sales_breakdown', reportType, effectiveFromDate, effectiveToDate],
    queryFn: () => tauriApi.getSalesBreakdown(reportType, effectiveFromDate, effectiveToDate),
  });

  const filteredData = useMemo(() => {
    if (!rawData || !rawData.length) return [];
    const minNum = minAmount !== '' ? parseFloat(minAmount) : null;
    const maxNum = maxAmount !== '' ? parseFloat(maxAmount) : null;
    const hasMin = minNum !== null && !isNaN(minNum);
    const hasMax = maxNum !== null && !isNaN(maxNum);

    return rawData.filter((row) => {
      let valPaise = 0;
      if (amountCol === 'gross') valPaise = row.total_sales_paise || 0;
      else if (amountCol === 'net')
        valPaise = (row.total_sales_paise || 0) - (row.total_discount_paise || 0);
      else if (amountCol === 'cash') valPaise = row.cash_paise || 0;
      else if (amountCol === 'upi') valPaise = row.upi_paise || 0;
      else if (amountCol === 'card') valPaise = row.card_paise || 0;
      else if (amountCol === 'gift_card') valPaise = row.gift_card_paise || 0;

      const valRs = valPaise / 100;
      if (hasMin && valRs < minNum) return false;
      if (hasMax && valRs > maxNum) return false;
      return true;
    });
  }, [rawData, amountCol, minAmount, maxAmount]);

  const totals = useMemo(() => {
    return filteredData.reduce(
      (acc, r) => {
        acc.transactions += r.total_transactions || 0;
        acc.cash += r.cash_paise || 0;
        acc.upi += r.upi_paise || 0;
        acc.card += r.card_paise || 0;
        acc.giftCard += r.gift_card_paise || 0;
        acc.discount += r.total_discount_paise || 0;
        acc.gross += r.total_sales_paise || 0;
        acc.net += Math.max(0, (r.total_sales_paise || 0) - (r.total_discount_paise || 0));
        return acc;
      },
      { transactions: 0, cash: 0, upi: 0, card: 0, giftCard: 0, discount: 0, gross: 0, net: 0 }
    );
  }, [filteredData]);

  const ALL_COLUMNS = useMemo(
    () => [
      {
        key: 'period',
        label: strings.salesReports.columns.periodLabel(reportType),
        className: 'font-mono text-text-primary font-semibold text-xs whitespace-nowrap',
        minWidth: 120,
        exportValue: (r) => (reportType === 'yearly' ? r.period : formatDate(r.period)),
        render: (r) => (reportType === 'yearly' ? r.period : formatDate(r.period)),
      },
      {
        key: 'total_transactions',
        label: strings.salesReports.columns.billsCount,
        headerClassName: 'text-center',
        className: 'text-center text-xs font-mono',
        minWidth: 90,
        exportValue: (r) => r.total_transactions,
        render: (r) => r.total_transactions,
        isNumeric: true,
        totalKey: 'transactions',
      },
      {
        key: 'cash_paise',
        label: strings.salesReports.columns.cashSettled,
        headerClassName: 'text-right',
        className: 'text-right financial-value text-text-secondary text-xs',
        minWidth: 110,
        exportValue: (r) => (r.cash_paise / 100).toFixed(2),
        render: (r) => formatPaise(r.cash_paise),
        isNumeric: true,
        totalKey: 'cash',
      },
      {
        key: 'upi_paise',
        label: strings.salesReports.columns.upiQr,
        headerClassName: 'text-right',
        className: 'text-right financial-value text-text-secondary text-xs',
        minWidth: 110,
        exportValue: (r) => (r.upi_paise / 100).toFixed(2),
        render: (r) => formatPaise(r.upi_paise),
        isNumeric: true,
        totalKey: 'upi',
      },
      {
        key: 'card_paise',
        label: strings.salesReports.columns.cardTerminal,
        headerClassName: 'text-right',
        className: 'text-right financial-value text-text-secondary text-xs',
        minWidth: 110,
        exportValue: (r) => (r.card_paise / 100).toFixed(2),
        render: (r) => formatPaise(r.card_paise),
        isNumeric: true,
        totalKey: 'card',
      },
      {
        key: 'gift_card_paise',
        label: strings.salesReports.columns.giftCard,
        headerClassName: 'text-right',
        className: 'text-right financial-value text-text-secondary text-xs',
        minWidth: 100,
        exportValue: (r) => (r.gift_card_paise / 100).toFixed(2),
        render: (r) => formatPaise(r.gift_card_paise),
        isNumeric: true,
        totalKey: 'giftCard',
      },
      {
        key: 'total_discount_paise',
        label: strings.salesReports.columns.discounts,
        headerClassName: 'text-right',
        className: 'text-right financial-value text-amber-600 text-xs',
        minWidth: 100,
        exportValue: (r) => (r.total_discount_paise / 100).toFixed(2),
        render: (r) => (r.total_discount_paise > 0 ? `-${formatPaise(r.total_discount_paise)}` : strings.common.states.emptyValue),
        isNumeric: true,
        totalKey: 'discount',
      },
      {
        key: 'total_sales_paise',
        label: strings.salesReports.columns.grossSales,
        headerClassName: 'text-right',
        className: 'text-right financial-value text-text-primary font-medium text-xs',
        minWidth: 120,
        exportValue: (r) => (r.total_sales_paise / 100).toFixed(2),
        render: (r) => formatPaise(r.total_sales_paise),
        isNumeric: true,
        totalKey: 'gross',
      },
      {
        key: 'net_sales',
        label: strings.salesReports.columns.netSales,
        headerClassName: 'text-right',
        className: 'text-right financial-value font-bold text-text-primary text-xs',
        minWidth: 120,
        exportValue: (r) => ((r.total_sales_paise - r.total_discount_paise) / 100).toFixed(2),
        render: (r) => formatPaise(Math.max(0, r.total_sales_paise - r.total_discount_paise)),
        sortValue: (r) => Math.max(0, (r.total_sales_paise || 0) - (r.total_discount_paise || 0)),
        isNumeric: true,
        totalKey: 'net',
      },
    ],
    [reportType]
  );

  const visibleColumns = useMemo(
    () => ALL_COLUMNS.filter((col) => visibleColKeys.includes(col.key)),
    [ALL_COLUMNS, visibleColKeys]
  );

  const toggleColumn = (key) => {
    setVisibleColKeys((prev) => {
      if (prev.includes(key)) {
        if (prev.length <= 1) return prev;
        return prev.filter((k) => k !== key);
      }
      return [...prev, key];
    });
  };

  const selectAllColumns = () => {
    setVisibleColKeys(ALL_COLUMNS.map((c) => c.key));
  };

  const handleReset = () => {
    setReportType('today');
    setFromDate('');
    setToDate('');
    setAmountCol('gross');
    setMinAmount('');
    setMaxAmount('');
  };

  const handleExportCsv = async () => {
    if (!filteredData.length || !visibleColumns.length) {
      toast.warning(strings.salesReports.export.noData);
      return;
    }

    const headers = visibleColumns.map((c) => escapeCsv(c.label));
    const rows = filteredData.map((row) =>
      visibleColumns.map((col) => escapeCsv(col.exportValue(row)))
    );

    const dateRangeLabel = fromDate && toDate
      ? `${fromDate}_to_${toDate}`
      : fromDate
        ? `from_${fromDate}`
        : toDate
          ? `to_${toDate}`
          : reportType === 'today'
            ? todayIso
            : 'all_time';

    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\r\n');
    await exportWithUserLocation({
      filename: strings.salesReports.export.filename(reportType, dateRangeLabel, 'csv'),
      extension: 'csv',
      data: csvContent,
      count: filteredData.length,
      entityName: strings.salesReports.export.entityName,
    });
  };

  const handleExportXlsx = async () => {
    if (!filteredData.length || !visibleColumns.length) {
      toast.warning(strings.salesReports.export.noData);
      return;
    }

    const headers = visibleColumns.map((c) => c.label);
    const rows = filteredData.map((row) =>
      visibleColumns.map((col) => {
        const val = col.exportValue(row);
        if (col.isNumeric && !isNaN(val)) return Number(val);
        return val;
      })
    );

    const ws = XLSX.utils.aoa_to_sheet([headers, ...rows]);
    ws['!cols'] = visibleColumns.map((col) => ({
      wch: Math.max(col.label.length + 4, 14),
    }));

    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, strings.salesReports.export.sheetName);
    const xlsxArrayBuffer = XLSX.write(wb, { type: 'array', bookType: 'xlsx' });
    const dateRangeLabel = fromDate && toDate
      ? `${fromDate}_to_${toDate}`
      : fromDate
        ? `from_${fromDate}`
        : toDate
          ? `to_${toDate}`
          : reportType === 'today'
            ? todayIso
            : 'all_time';

    await exportWithUserLocation({
      filename: strings.salesReports.export.filename(reportType, dateRangeLabel, 'xlsx'),
      extension: 'xlsx',
      data: xlsxArrayBuffer,
      count: filteredData.length,
      entityName: strings.salesReports.export.entityName,
    });
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas overflow-hidden">
      {/* ── Row 1: Header & Export Controls ── */}
      <div className="h-14 px-5 border-b border-border-default bg-surface flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-brand-subtle text-brand flex items-center justify-center">
            <Calendar className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-text-primary">{strings.salesReports.view.title}</h2>
            <p className="text-[11px] text-text-muted">{strings.salesReports.view.subtitle}</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Column Picker */}
          <div ref={colMenuRef} className="relative">
            <button
              type="button"
              onClick={() => setColMenuOpen((o) => !o)}
              className="px-3 py-1.5 rounded-lg bg-surface hover:bg-surface-secondary text-text-secondary hover:text-text-primary font-medium text-xs flex items-center gap-1.5 border border-border-default transition-colors cursor-pointer shadow-2xs"
            >
              <SlidersHorizontal className="w-3.5 h-3.5 text-text-muted" />
              <span>{strings.salesReports.view.columnPicker(visibleColKeys.length)}</span>
              <ChevronDown
                className={`w-3 h-3 text-text-muted transition-transform ${colMenuOpen ? 'rotate-180' : ''}`}
              />
            </button>

            {colMenuOpen && (
              <div className="absolute right-0 top-full mt-1.5 z-50 bg-surface border border-border-default rounded-xl shadow-xl p-2.5 min-w-[210px] space-y-1 text-text-primary animate-scale-in">
                <div className="flex items-center justify-between px-2 pb-2 mb-1 border-b border-border-default text-[11px]">
                  <span className="font-semibold text-text-primary">{strings.salesReports.view.visibleColumns}</span>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={selectAllColumns}
                      className="text-brand hover:underline cursor-pointer"
                    >
                      All
                    </button>
                    <span className="text-text-muted">|</span>
                    <button
                      type="button"
                      onClick={() => setVisibleColKeys(defaultColKeys)}
                      className="text-text-muted hover:text-text-primary cursor-pointer"
                    >
                      Reset
                    </button>
                  </div>
                </div>

                <div className="space-y-0.5 max-h-64 overflow-y-auto pr-1">
                  {ALL_COLUMNS.map((col) => {
                    const isChecked = visibleColKeys.includes(col.key);
                    return (
                      <button
                        key={col.key}
                        type="button"
                        onClick={() => toggleColumn(col.key)}
                        className={`w-full flex items-center gap-2.5 px-2.5 py-1.5 rounded-lg text-xs text-left transition-colors cursor-pointer ${
                          isChecked
                            ? 'bg-brand-subtle text-brand font-medium'
                            : 'text-text-secondary hover:bg-surface-secondary hover:text-text-primary'
                        }`}
                      >
                        <span
                          className={`w-3.5 h-3.5 rounded border flex items-center justify-center shrink-0 ${
                            isChecked ? 'bg-brand border-brand' : 'border-border-strong'
                          }`}
                        >
                          {isChecked && <Check className="w-2.5 h-2.5 text-white" strokeWidth={3} />}
                        </span>
                        <span>{col.label}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
          </div>

          <ExportButton
            onExportCsv={handleExportCsv}
            onExportXlsx={handleExportXlsx}
            disabled={!filteredData.length || !visibleColumns.length}
          />

          <button
            type="button"
            onClick={() => refetch()}
            className="p-1.5 rounded-lg border border-border-default text-text-muted hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
            title={strings.salesReports.view.refreshReport}
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* ── Row 2: Period & Date Range Toolbar ── */}
      <div className="px-5 py-2.5 border-b border-border-default bg-surface flex items-center gap-3 flex-wrap text-xs shrink-0">
        <div className="flex items-center gap-1.5">
          <span className="text-text-muted font-medium">{strings.salesReports.filters.period}</span>
          <Select
            value={reportType}
            onChange={(val) => setReportType(val)}
            options={[
              { value: 'today', label: strings.salesReports.filters.reportTypes.today },
              { value: 'daily', label: strings.salesReports.filters.reportTypes.daily },
              { value: 'monthly', label: strings.salesReports.filters.reportTypes.monthly },
              { value: 'yearly', label: strings.salesReports.filters.reportTypes.yearly },
            ]}
          />
        </div>

        <div className="flex items-center gap-1.5">
          <span className="text-text-muted font-medium">{strings.salesReports.filters.from}</span>
          <DatePicker
            value={fromDate}
            onChange={(val) => setFromDate(val)}
            placeholder={strings.salesReports.filters.fromDatePlaceholder}
          />
        </div>

        <div className="flex items-center gap-1.5">
          <span className="text-text-muted font-medium">{strings.salesReports.filters.to}</span>
          <DatePicker
            value={toDate}
            onChange={(val) => setToDate(val)}
            placeholder={strings.salesReports.filters.toDatePlaceholder}
          />
        </div>

        <button
          type="button"
          onClick={() => setAdvancedOpen((o) => !o)}
          className={`flex items-center gap-1 px-2.5 py-1.5 rounded-lg border text-xs cursor-pointer transition-colors ${
            advancedOpen || minAmount || maxAmount
              ? 'bg-brand-subtle text-brand border-brand/30'
              : 'bg-surface text-text-secondary border-border-default hover:border-border-strong'
          }`}
        >
          <Filter className="w-3 h-3" />
          <span>{strings.salesReports.filters.advancedFilters}</span>
          {advancedOpen ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
        </button>

        <button
          type="button"
          onClick={handleReset}
          className="text-text-muted hover:text-text-primary px-2 py-1 rounded hover:bg-surface-secondary transition-colors ml-auto flex items-center gap-1 cursor-pointer"
        >
          <RotateCcw className="w-3 h-3" />
          <span>{strings.common.actions.reset}</span>
        </button>
      </div>

      {/* ── Row 3: Collapsible Advanced Filters ── */}
      {advancedOpen && (
        <div className="px-5 py-2.5 bg-surface-secondary border-b border-border-default flex items-center gap-3 text-xs flex-wrap animate-fade-in shrink-0">
          <span className="text-text-muted font-semibold">Filter Threshold:</span>
          <Select
            value={amountCol}
            onChange={(val) => setAmountCol(val)}
            options={[
              { value: 'gross', label: 'Gross Sales' },
              { value: 'net', label: 'Net Sales' },
              { value: 'cash', label: 'Cash' },
              { value: 'upi', label: 'UPI' },
              { value: 'card', label: 'Card' },
              { value: 'gift_card', label: 'Gift Card' },
            ]}
          />
          <div className="flex items-center gap-1.5">
            <input
              type="number"
              step="any"
              placeholder={`Min ${currencySymbol}`}
              value={minAmount}
              onChange={(e) => setMinAmount(e.target.value)}
              onInput={(e) => setMinAmount(e.target.value)}
              className="w-20 bg-surface border border-border-strong text-text-primary rounded-lg px-2 py-1 font-mono focus:border-brand focus:outline-none"
            />
            <span className="text-text-muted">–</span>
            <input
              type="number"
              step="any"
              placeholder={`Max ${currencySymbol}`}
              value={maxAmount}
              onChange={(e) => setMaxAmount(e.target.value)}
              onInput={(e) => setMaxAmount(e.target.value)}
              className="w-20 bg-surface border border-border-strong text-text-primary rounded-lg px-2 py-1 font-mono focus:border-brand focus:outline-none"
            />
            {(minAmount !== '' || maxAmount !== '') && (
              <button
                type="button"
                onClick={() => {
                  setMinAmount('');
                  setMaxAmount('');
                }}
                className="text-danger hover:underline text-xs cursor-pointer ml-1"
              >
                Clear
              </button>
            )}
          </div>
        </div>
      )}

      {/* ── Main Breakdown Table ── */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={visibleColumns}
          persistKey="sales-reports"
          data={filteredData}
          keyFn={(r) => r.period}
          searchKeys={['period']}
          pageSize={25}
          emptyMessage="No sales records found for the selected period."
          isLoading={isLoading}
        />
      </div>

      {/* ── Sticky Financial Totals Bar ── */}
      <div className="border-t border-border-default bg-surface px-5 py-2.5 flex items-center justify-between flex-wrap gap-x-6 gap-y-2 text-xs shrink-0 shadow-xs z-20">
        <div className="flex items-center gap-2">
          <span className="font-bold text-text-primary uppercase tracking-wide">
            {strings.salesReports.summary.totals}
          </span>
        </div>

        <div className="flex items-center gap-4 flex-wrap">
          {visibleColumns.map((col) => {
            if (!col.totalKey) return null;
            let displayVal = '';

            if (col.totalKey === 'transactions') {
              displayVal = strings.salesReports.summary.transactions(totals.transactions);
            } else if (col.totalKey === 'cash') {
              displayVal = formatPaise(totals.cash);
            } else if (col.totalKey === 'upi') {
              displayVal = formatPaise(totals.upi);
            } else if (col.totalKey === 'card') {
              displayVal = formatPaise(totals.card);
            } else if (col.totalKey === 'giftCard') {
              displayVal = formatPaise(totals.giftCard);
            } else if (col.totalKey === 'discount') {
              displayVal = formatPaise(totals.discount);
            } else if (col.totalKey === 'gross') {
              displayVal = formatPaise(totals.gross);
            } else if (col.totalKey === 'net') {
              displayVal = formatPaise(totals.net);
            }

            return (
              <div
                key={col.key}
                className="flex items-center gap-1.5 bg-surface-secondary px-2 py-1 rounded-md border border-border-default"
              >
                <span className="text-[10px] text-text-muted uppercase tracking-wider">{col.label}:</span>
                <span className="font-semibold text-text-primary financial-value">{displayVal}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
