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
import { formatPaise, getCurrencySymbol } from '../../../lib/formatters';
import { toast } from '../../../stores/toast.store';
import DataTable from '../../../components/common/DataTable';
import Select from '../../../components/common/Select';
import DatePicker from '../../../components/common/DatePicker';
import ExportButton from '../../../components/common/ExportButton';
import { exportWithUserLocation, escapeCsv } from '../../../lib/exportUtils';

export default function SalesReportsView() {
  const currencySymbol = getCurrencySymbol();
  const todayStr = new Date().toISOString().split('T')[0];
  const firstOfMonthStr = new Date(new Date().getFullYear(), new Date().getMonth(), 1)
    .toISOString()
    .split('T')[0];

  const [reportType, setReportType] = useState('daily');
  const [fromDate, setFromDate] = useState(firstOfMonthStr);
  const [toDate, setToDate] = useState(todayStr);
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

  const { data: rawData = [], isLoading, refetch } = useQuery({
    queryKey: ['sales_breakdown', reportType, fromDate, toDate],
    queryFn: () => tauriApi.getSalesBreakdown(reportType, fromDate || null, toDate || null),
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
        label: reportType === 'daily' ? 'Date' : reportType === 'monthly' ? 'Month' : 'Year',
        className: 'font-mono text-text-primary font-semibold text-xs whitespace-nowrap',
        minWidth: 120,
        exportValue: (r) => r.period,
        render: (r) => r.period,
      },
      {
        key: 'total_transactions',
        label: 'Bills Count',
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
        label: 'Cash Settled',
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
        label: 'UPI / QR',
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
        label: 'Card Terminal',
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
        label: 'Gift Card',
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
        label: 'Discounts',
        headerClassName: 'text-right',
        className: 'text-right financial-value text-amber-600 text-xs',
        minWidth: 100,
        exportValue: (r) => (r.total_discount_paise / 100).toFixed(2),
        render: (r) => (r.total_discount_paise > 0 ? `-${formatPaise(r.total_discount_paise)}` : '—'),
        isNumeric: true,
        totalKey: 'discount',
      },
      {
        key: 'total_sales_paise',
        label: 'Gross Sales',
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
        label: 'Net Sales',
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
    setReportType('daily');
    setFromDate(firstOfMonthStr);
    setToDate(todayStr);
    setAmountCol('gross');
    setMinAmount('');
    setMaxAmount('');
  };

  const handleExportCsv = async () => {
    if (!filteredData.length || !visibleColumns.length) {
      toast.warning('No report data to export');
      return;
    }

    const headers = visibleColumns.map((c) => escapeCsv(c.label));
    const rows = filteredData.map((row) =>
      visibleColumns.map((col) => escapeCsv(col.exportValue(row)))
    );

    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\r\n');
    await exportWithUserLocation({
      filename: `sales_report_${reportType}_${fromDate}_to_${toDate}.csv`,
      extension: 'csv',
      data: csvContent,
      count: filteredData.length,
      entityName: 'report rows',
    });
  };

  const handleExportXlsx = async () => {
    if (!filteredData.length || !visibleColumns.length) {
      toast.warning('No report data to export');
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
    XLSX.utils.book_append_sheet(wb, ws, 'Sales Report');
    const xlsxArrayBuffer = XLSX.write(wb, { type: 'array', bookType: 'xlsx' });
    await exportWithUserLocation({
      filename: `sales_report_${reportType}_${fromDate}_to_${toDate}.xlsx`,
      extension: 'xlsx',
      data: xlsxArrayBuffer,
      count: filteredData.length,
      entityName: 'report rows',
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
            <h2 className="text-sm font-bold text-text-primary">Financial Sales Reports</h2>
            <p className="text-[11px] text-text-muted">Periodic reconciliation and payment collection audits</p>
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
              <span>Columns ({visibleColKeys.length})</span>
              <ChevronDown
                className={`w-3 h-3 text-text-muted transition-transform ${colMenuOpen ? 'rotate-180' : ''}`}
              />
            </button>

            {colMenuOpen && (
              <div className="absolute right-0 top-full mt-1.5 z-50 bg-surface border border-border-default rounded-xl shadow-xl p-2.5 min-w-[210px] space-y-1 text-text-primary animate-scale-in">
                <div className="flex items-center justify-between px-2 pb-2 mb-1 border-b border-border-default text-[11px]">
                  <span className="font-semibold text-text-primary">Visible Columns</span>
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

          <button
            type="button"
            onClick={() => refetch()}
            className="p-1.5 rounded-lg border border-border-default text-text-muted hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
            title="Refresh Report"
          >
            <RefreshCw className="w-4 h-4" />
          </button>

          <ExportButton
            onExportCsv={handleExportCsv}
            onExportXlsx={handleExportXlsx}
            disabled={!filteredData.length || !visibleColumns.length}
          />
        </div>
      </div>

      {/* ── Row 2: Period & Date Range Toolbar ── */}
      <div className="px-5 py-2.5 border-b border-border-default bg-surface flex items-center gap-3 flex-wrap text-xs shrink-0">
        <div className="flex items-center gap-1.5">
          <span className="text-text-muted font-medium">Period:</span>
          <Select
            value={reportType}
            onChange={(val) => setReportType(val)}
            options={[
              { value: 'daily', label: 'Daily' },
              { value: 'monthly', label: 'Monthly' },
              { value: 'yearly', label: 'Yearly' },
            ]}
          />
        </div>

        <div className="flex items-center gap-1.5">
          <span className="text-text-muted font-medium">From:</span>
          <DatePicker
            value={fromDate}
            onChange={(val) => setFromDate(val)}
            placeholder="From date"
          />
        </div>

        <div className="flex items-center gap-1.5">
          <span className="text-text-muted font-medium">To:</span>
          <DatePicker
            value={toDate}
            onChange={(val) => setToDate(val)}
            placeholder="To date"
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
          <span>Advanced Filters</span>
          {advancedOpen ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
        </button>

        <button
          type="button"
          onClick={handleReset}
          className="text-text-muted hover:text-text-primary px-2 py-1 rounded hover:bg-surface-secondary transition-colors ml-auto flex items-center gap-1 cursor-pointer"
        >
          <RotateCcw className="w-3 h-3" />
          <span>Reset</span>
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
          <span className="font-bold text-text-primary uppercase tracking-wide">Totals</span>
        </div>

        <div className="flex items-center gap-4 flex-wrap">
          {visibleColumns.map((col) => {
            if (!col.totalKey) return null;
            let displayVal = '';

            if (col.totalKey === 'transactions') {
              displayVal = `${totals.transactions} txns`;
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
