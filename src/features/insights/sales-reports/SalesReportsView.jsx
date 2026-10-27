import { useState, useMemo, useRef, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Calendar,
  Download,
  RefreshCw,
  SlidersHorizontal,
  FileSpreadsheet,
  FileText,
  Check,
  RotateCcw,
  ChevronDown,
  X,
} from 'lucide-react';
import * as XLSX from 'xlsx';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise, getCurrencySymbol } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';

export default function SalesReportsView() {
  const currencySymbol = getCurrencySymbol();
  const todayStr = new Date().toISOString().split('T')[0];
  const firstOfMonthStr = new Date(new Date().getFullYear(), new Date().getMonth(), 1)
    .toISOString()
    .split('T')[0];

  const [reportType, setReportType] = useState('daily'); // 'daily' | 'monthly' | 'yearly'
  const [fromDate, setFromDate] = useState(firstOfMonthStr);
  const [toDate, setToDate] = useState(todayStr);

  // Amount filtering
  const [amountCol, setAmountCol] = useState('gross'); // 'gross' | 'net' | 'cash' | 'upi' | 'card'
  const [minAmount, setMinAmount] = useState('');
  const [maxAmount, setMaxAmount] = useState('');

  // Column visibility state (all enabled by default)
  const defaultColKeys = [
    'period',
    'total_transactions',
    'cash_paise',
    'upi_paise',
    'card_paise',
    'other_paise',
    'total_discount_paise',
    'total_sales_paise',
    'net_sales',
  ];
  const [visibleColKeys, setVisibleColKeys] = useState(defaultColKeys);
  const [colMenuOpen, setColMenuOpen] = useState(false);
  const colMenuRef = useRef(null);

  // Close dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (colMenuRef.current && !colMenuRef.current.contains(e.target)) {
        setColMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const { data: rawBreakdown = [], isLoading, refetch } = useQuery({
    queryKey: ['sales_breakdown', reportType, fromDate, toDate],
    queryFn: () => tauriApi.getSalesBreakdown(reportType, fromDate || null, toDate || null),
  });

  // Client-side amount filtering
  const filteredData = useMemo(() => {
    let rows = rawBreakdown;
    const minPaise = minAmount !== '' && !isNaN(minAmount) ? Math.round(parseFloat(minAmount) * 100) : null;
    const maxPaise = maxAmount !== '' && !isNaN(maxAmount) ? Math.round(parseFloat(maxAmount) * 100) : null;

    if (minPaise !== null || maxPaise !== null) {
      rows = rows.filter((r) => {
        let val = r.total_sales_paise;
        if (amountCol === 'cash') val = r.cash_paise;
        else if (amountCol === 'upi') val = r.upi_paise;
        else if (amountCol === 'card') val = r.card_paise;
        else if (amountCol === 'net') val = Math.max(0, r.total_sales_paise - r.total_discount_paise);
        else if (amountCol === 'gross') val = r.total_sales_paise;

        if (minPaise !== null && val < minPaise) return false;
        if (maxPaise !== null && val > maxPaise) return false;
        return true;
      });
    }

    return rows;
  }, [rawBreakdown, amountCol, minAmount, maxAmount]);

  // Aggregate totals across filtered data
  const totals = useMemo(() => {
    return filteredData.reduce(
      (acc, r) => {
        acc.transactions += r.total_transactions || 0;
        acc.cash += r.cash_paise || 0;
        acc.upi += r.upi_paise || 0;
        acc.card += r.card_paise || 0;
        acc.other += r.other_paise || 0;
        acc.discount += r.total_discount_paise || 0;
        acc.gross += r.total_sales_paise || 0;
        acc.net += Math.max(0, (r.total_sales_paise || 0) - (r.total_discount_paise || 0));
        return acc;
      },
      { transactions: 0, cash: 0, upi: 0, card: 0, other: 0, discount: 0, gross: 0, net: 0 }
    );
  }, [filteredData]);

  // Column definitions with labels, formatting, and numeric properties
  const ALL_COLUMNS = useMemo(
    () => [
      {
        key: 'period',
        label: reportType === 'daily' ? 'Date' : reportType === 'monthly' ? 'Month' : 'Year',
        className: 'font-mono font-semibold text-slate-200',
        minWidth: 120,
        exportValue: (r) => r.period,
      },
      {
        key: 'total_transactions',
        label: 'Transactions',
        headerClassName: 'text-center',
        className: 'text-center font-mono text-slate-300',
        minWidth: 100,
        exportValue: (r) => r.total_transactions,
        render: (r) => r.total_transactions,
        isNumeric: true,
        totalKey: 'transactions',
      },
      {
        key: 'cash_paise',
        label: 'Cash',
        headerClassName: 'text-right',
        className: 'text-right font-mono text-emerald-400',
        minWidth: 100,
        exportValue: (r) => (r.cash_paise / 100).toFixed(2),
        render: (r) => formatPaise(r.cash_paise),
        isNumeric: true,
        totalKey: 'cash',
      },
      {
        key: 'upi_paise',
        label: 'UPI',
        headerClassName: 'text-right',
        className: 'text-right font-mono text-sky-400',
        minWidth: 100,
        exportValue: (r) => (r.upi_paise / 100).toFixed(2),
        render: (r) => formatPaise(r.upi_paise),
        isNumeric: true,
        totalKey: 'upi',
      },
      {
        key: 'card_paise',
        label: 'Card',
        headerClassName: 'text-right',
        className: 'text-right font-mono text-indigo-400',
        minWidth: 100,
        exportValue: (r) => (r.card_paise / 100).toFixed(2),
        render: (r) => formatPaise(r.card_paise),
        isNumeric: true,
        totalKey: 'card',
      },
      {
        key: 'other_paise',
        label: 'Other',
        headerClassName: 'text-right',
        className: 'text-right font-mono text-amber-400',
        minWidth: 100,
        exportValue: (r) => (r.other_paise / 100).toFixed(2),
        render: (r) => formatPaise(r.other_paise),
        isNumeric: true,
        totalKey: 'other',
      },
      {
        key: 'total_discount_paise',
        label: 'Discounts',
        headerClassName: 'text-right',
        className: 'text-right font-mono text-rose-400/80',
        minWidth: 100,
        exportValue: (r) => (r.total_discount_paise / 100).toFixed(2),
        render: (r) => formatPaise(r.total_discount_paise),
        isNumeric: true,
        totalKey: 'discount',
      },
      {
        key: 'total_sales_paise',
        label: 'Gross Sales',
        headerClassName: 'text-right',
        className: 'text-right font-mono text-slate-200 font-bold',
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
        className: 'text-right font-mono text-emerald-400 font-bold',
        minWidth: 120,
        exportValue: (r) => ((r.total_sales_paise - r.total_discount_paise) / 100).toFixed(2),
        render: (r) => formatPaise(Math.max(0, r.total_sales_paise - r.total_discount_paise)),
        isNumeric: true,
        totalKey: 'net',
      },
    ],
    [reportType]
  );

  // Active visible columns for view and export
  const visibleColumns = useMemo(
    () => ALL_COLUMNS.filter((col) => visibleColKeys.includes(col.key)),
    [ALL_COLUMNS, visibleColKeys]
  );

  const toggleColumn = (key) => {
    setVisibleColKeys((prev) => {
      if (prev.includes(key)) {
        if (prev.length <= 1) return prev; // At least one column must remain visible
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

  // CSV Export (Selected columns only)
  const handleExportCsv = () => {
    if (!filteredData.length || !visibleColumns.length) return;

    const escapeCsv = (val) => {
      const str = String(val ?? '');
      if (str.includes(',') || str.includes('"') || str.includes('\n') || str.includes('\r')) {
        return `"${str.replace(/"/g, '""')}"`;
      }
      return str;
    };

    const headers = visibleColumns.map((c) => escapeCsv(c.label));
    const rows = filteredData.map((row) =>
      visibleColumns.map((col) => escapeCsv(col.exportValue(row)))
    );

    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `sales_report_${reportType}_${fromDate}_to_${toDate}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  // XLSX Export (Selected columns only)
  const handleExportXlsx = () => {
    if (!filteredData.length || !visibleColumns.length) return;

    const headers = visibleColumns.map((c) => c.label);
    const rows = filteredData.map((row) =>
      visibleColumns.map((col) => {
        const val = col.exportValue(row);
        if (col.isNumeric && !isNaN(val)) {
          return Number(val);
        }
        return val;
      })
    );

    const ws = XLSX.utils.aoa_to_sheet([headers, ...rows]);
    // Auto-fit column widths
    ws['!cols'] = visibleColumns.map((col) => ({
      wch: Math.max(col.label.length + 4, 14),
    }));

    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Sales Report');
    XLSX.writeFile(wb, `sales_report_${reportType}_${fromDate}_to_${toDate}.xlsx`);
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* ── Top Header ── */}
      <div className="h-14 px-5 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <Calendar className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-100">Sales Reports</h2>
            <p className="text-[11px] text-slate-500">Periodic financial revenue and payment method breakdown</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Column Selection Dropdown */}
          <div ref={colMenuRef} className="relative">
            <button
              type="button"
              onClick={() => setColMenuOpen((o) => !o)}
              className="px-3 py-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-200 font-medium text-xs flex items-center gap-1.5 border border-slate-700/60 transition-all cursor-pointer"
              title="Select columns to display and export"
            >
              <SlidersHorizontal className="w-3.5 h-3.5 text-emerald-400" />
              <span>
                Columns ({visibleColKeys.length}/{ALL_COLUMNS.length})
              </span>
              <ChevronDown className={`w-3 h-3 text-slate-400 transition-transform ${colMenuOpen ? 'rotate-180' : ''}`} />
            </button>

            {colMenuOpen && (
              <div className="absolute right-0 top-full mt-1.5 z-50 bg-slate-900 border border-slate-700 rounded-xl shadow-2xl shadow-black/60 p-2.5 min-w-[210px] space-y-1">
                <div className="flex items-center justify-between px-2 pb-2 mb-1 border-b border-slate-800 text-[11px]">
                  <span className="font-semibold text-slate-300">Visible Columns</span>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={selectAllColumns}
                      className="text-emerald-400 hover:underline cursor-pointer"
                    >
                      All
                    </button>
                    <span className="text-slate-600">|</span>
                    <button
                      type="button"
                      onClick={() => setVisibleColKeys(defaultColKeys)}
                      className="text-slate-400 hover:text-slate-200 cursor-pointer"
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
                            ? 'bg-emerald-500/10 text-emerald-300'
                            : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
                        }`}
                      >
                        <span
                          className={`w-3.5 h-3.5 rounded border flex items-center justify-center flex-shrink-0 ${
                            isChecked ? 'bg-emerald-500 border-emerald-500' : 'border-slate-600'
                          }`}
                        >
                          {isChecked && <Check className="w-2.5 h-2.5 text-slate-950" strokeWidth={3} />}
                        </span>
                        <span>{col.label}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
          </div>

          {/* Refresh */}
          <button
            onClick={() => refetch()}
            className="p-1.5 rounded-lg border border-slate-800 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
            title="Refresh Report"
          >
            <RefreshCw className="w-4 h-4" />
          </button>

          {/* Export CSV */}
          <button
            onClick={handleExportCsv}
            disabled={!filteredData.length || !visibleColumns.length}
            className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs flex items-center gap-1.5 border border-slate-700/60 transition-all disabled:opacity-40 cursor-pointer"
            title="Export selected columns as CSV"
          >
            <FileText className="w-3.5 h-3.5 text-sky-400" />
            <span>CSV</span>
          </button>

          {/* Export XLSX */}
          <button
            onClick={handleExportXlsx}
            disabled={!filteredData.length || !visibleColumns.length}
            className="px-3 py-1.5 rounded-lg bg-emerald-600/90 hover:bg-emerald-500 text-slate-950 font-bold text-xs flex items-center gap-1.5 border border-emerald-500/40 shadow-sm transition-all disabled:opacity-40 cursor-pointer"
            title="Export selected columns as Excel XLSX"
          >
            <FileSpreadsheet className="w-3.5 h-3.5" />
            <span>Excel (XLSX)</span>
          </button>
        </div>
      </div>

      {/* ── Filter Toolbar ── */}
      <div className="px-5 py-2.5 border-b border-slate-800/80 bg-slate-900/30 flex items-center gap-3 flex-wrap text-xs">
        {/* Report Type */}
        <div className="flex items-center gap-1.5">
          <span className="text-slate-400 font-medium">Type:</span>
          <select
            value={reportType}
            onChange={(e) => setReportType(e.target.value)}
            className="bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2.5 py-1.5 focus:border-emerald-500 focus:outline-none cursor-pointer"
          >
            <option value="daily">Daily</option>
            <option value="monthly">Monthly</option>
            <option value="yearly">Yearly</option>
          </select>
        </div>

        {/* Date Pickers */}
        <div className="flex items-center gap-1.5">
          <span className="text-slate-400 font-medium">From:</span>
          <input
            type="date"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            className="bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2 py-1 focus:border-emerald-500 focus:outline-none font-mono"
          />
        </div>

        <div className="flex items-center gap-1.5">
          <span className="text-slate-400 font-medium">To:</span>
          <input
            type="date"
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            className="bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2 py-1 focus:border-emerald-500 focus:outline-none font-mono"
          />
        </div>

        {/* Amount Range Filter */}
        <div className="flex items-center gap-1.5">
          <span className="text-slate-400 font-medium">Amount on:</span>
          <select
            value={amountCol}
            onChange={(e) => setAmountCol(e.target.value)}
            className="bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2 py-1.5 focus:border-emerald-500 focus:outline-none cursor-pointer"
          >
            <option value="gross">Gross Sales</option>
            <option value="net">Net Sales</option>
            <option value="cash">Cash</option>
            <option value="upi">UPI</option>
            <option value="card">Card</option>
          </select>
        </div>

        <div className="flex items-center gap-1.5">
          <input
            type="number"
            placeholder={`Min ${currencySymbol}`}
            value={minAmount}
            onChange={(e) => setMinAmount(e.target.value)}
            className="w-20 bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2 py-1 font-mono focus:border-emerald-500 focus:outline-none"
          />
          <span className="text-slate-600">–</span>
          <input
            type="number"
            placeholder={`Max ${currencySymbol}`}
            value={maxAmount}
            onChange={(e) => setMaxAmount(e.target.value)}
            className="w-20 bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2 py-1 font-mono focus:border-emerald-500 focus:outline-none"
          />
          {(minAmount !== '' || maxAmount !== '') && (
            <button
              type="button"
              onClick={() => {
                setMinAmount('');
                setMaxAmount('');
              }}
              className="text-slate-500 hover:text-rose-400 transition-colors p-0.5 cursor-pointer"
              title="Clear amount filter"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        <button
          onClick={handleReset}
          className="text-slate-400 hover:text-white px-2.5 py-1 rounded-lg hover:bg-slate-800 transition-colors ml-auto flex items-center gap-1 cursor-pointer"
        >
          <RotateCcw className="w-3 h-3" />
          <span>Reset</span>
        </button>
      </div>

      {/* ── Summary KPI Cards Bar ── */}
      <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-2 p-3 bg-slate-900/40 border-b border-slate-800/80 flex-shrink-0">
        <div className="bg-slate-950/60 p-2.5 rounded-xl border border-slate-800/80">
          <span className="text-[10px] text-slate-500 font-medium uppercase tracking-wider block">Transactions</span>
          <span className="text-sm font-bold font-mono text-slate-200">{totals.transactions}</span>
        </div>
        <div className="bg-slate-950/60 p-2.5 rounded-xl border border-slate-800/80">
          <span className="text-[10px] text-emerald-500/80 font-medium uppercase tracking-wider block">Cash</span>
          <span className="text-sm font-bold font-mono text-emerald-400">{formatPaise(totals.cash)}</span>
        </div>
        <div className="bg-slate-950/60 p-2.5 rounded-xl border border-slate-800/80">
          <span className="text-[10px] text-sky-500/80 font-medium uppercase tracking-wider block">UPI</span>
          <span className="text-sm font-bold font-mono text-sky-400">{formatPaise(totals.upi)}</span>
        </div>
        <div className="bg-slate-950/60 p-2.5 rounded-xl border border-slate-800/80">
          <span className="text-[10px] text-indigo-500/80 font-medium uppercase tracking-wider block">Card</span>
          <span className="text-sm font-bold font-mono text-indigo-400">{formatPaise(totals.card)}</span>
        </div>
        <div className="bg-slate-950/60 p-2.5 rounded-xl border border-slate-800/80">
          <span className="text-[10px] text-rose-500/80 font-medium uppercase tracking-wider block">Discounts</span>
          <span className="text-sm font-bold font-mono text-rose-400">{formatPaise(totals.discount)}</span>
        </div>
        <div className="bg-slate-950/60 p-2.5 rounded-xl border border-slate-800/80">
          <span className="text-[10px] text-slate-400 font-medium uppercase tracking-wider block">Gross Sales</span>
          <span className="text-sm font-bold font-mono text-slate-100">{formatPaise(totals.gross)}</span>
        </div>
        <div className="bg-slate-950/60 p-2.5 rounded-xl border border-emerald-500/30 bg-emerald-500/5">
          <span className="text-[10px] text-emerald-400 font-bold uppercase tracking-wider block">Net Sales</span>
          <span className="text-sm font-black font-mono text-emerald-400">{formatPaise(totals.net)}</span>
        </div>
      </div>

      {/* ── Main Breakdown Table (Renders Selected Columns) ── */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={visibleColumns}
          persistKey="sales-reports"
          data={filteredData}
          keyFn={(r) => r.period}
          searchKeys={['period']}
          pageSize={25}
          emptyMessage="No sales records found for the selected criteria."
          isLoading={isLoading}
        />
      </div>

      {/* ── Totals Bar Below Table (Reflects Visible Columns) ── */}
      <div className="border-t border-slate-800 bg-slate-900/95 backdrop-blur-md px-5 py-2.5 flex items-center justify-between flex-wrap gap-x-6 gap-y-2 text-xs font-mono flex-shrink-0 shadow-lg shadow-black/40 z-20">
        <div className="flex items-center gap-2">
          <span className="font-bold text-slate-100 uppercase tracking-wide">Totals</span>
          <span className="text-slate-400 text-[11px]">
            ({filteredData.length} {reportType === 'daily' ? 'days' : reportType === 'monthly' ? 'months' : 'years'})
          </span>
        </div>

        <div className="flex items-center gap-4 flex-wrap">
          {visibleColumns.map((col) => {
            if (!col.totalKey) return null;
            let displayVal = '';
            let colorClass = 'text-slate-200';

            if (col.totalKey === 'transactions') {
              displayVal = `${totals.transactions} txns`;
            } else if (col.totalKey === 'cash') {
              displayVal = formatPaise(totals.cash);
              colorClass = 'text-emerald-400';
            } else if (col.totalKey === 'upi') {
              displayVal = formatPaise(totals.upi);
              colorClass = 'text-sky-400';
            } else if (col.totalKey === 'card') {
              displayVal = formatPaise(totals.card);
              colorClass = 'text-indigo-400';
            } else if (col.totalKey === 'other') {
              displayVal = formatPaise(totals.other);
              colorClass = 'text-amber-400';
            } else if (col.totalKey === 'discount') {
              displayVal = formatPaise(totals.discount);
              colorClass = 'text-rose-400';
            } else if (col.totalKey === 'gross') {
              displayVal = formatPaise(totals.gross);
              colorClass = 'text-slate-100 font-bold';
            } else if (col.totalKey === 'net') {
              displayVal = formatPaise(totals.net);
              colorClass = 'text-emerald-400 font-bold';
            }

            return (
              <div key={col.key} className="flex items-center gap-1.5 bg-slate-950/50 px-2 py-1 rounded-md border border-slate-800">
                <span className="text-[10px] text-slate-500 uppercase tracking-wider">{col.label}:</span>
                <span className={`font-semibold ${colorClass}`}>{displayVal}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
