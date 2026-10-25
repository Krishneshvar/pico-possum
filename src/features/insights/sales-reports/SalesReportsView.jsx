import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Calendar, Download, RefreshCw, Filter, IndianRupee, CreditCard, Banknote, QrCode } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';

export default function SalesReportsView() {
  const todayStr = new Date().toISOString().split('T')[0];
  const firstOfMonthStr = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().split('T')[0];

  const [reportType, setReportType] = useState('daily'); // 'daily' | 'monthly' | 'yearly'
  const [fromDate, setFromDate] = useState(firstOfMonthStr);
  const [toDate, setToDate] = useState(todayStr);
  const [amountCol, setAmountCol] = useState('none'); // 'none' | 'cash' | 'upi' | 'card' | 'gross'
  const [minAmount, setMinAmount] = useState('');
  const [maxAmount, setMaxAmount] = useState('');

  const { data: rawBreakdown = [], isLoading, refetch } = useQuery({
    queryKey: ['sales_breakdown', reportType, fromDate, toDate],
    queryFn: () => tauriApi.getSalesBreakdown(reportType, fromDate || null, toDate || null),
  });

  // Client-side amount filtering matching Java SalesReportsController
  const filteredData = useMemo(() => {
    let rows = rawBreakdown;
    const minPaise = minAmount ? parseFloat(minAmount) * 100 : null;
    const maxPaise = maxAmount ? parseFloat(maxAmount) * 100 : null;

    if (amountCol !== 'none') {
      rows = rows.filter((r) => {
        let val = 0;
        if (amountCol === 'cash') val = r.cash_paise;
        else if (amountCol === 'upi') val = r.upi_paise;
        else if (amountCol === 'card') val = r.card_paise;
        else if (amountCol === 'gross') val = r.total_sales_paise;

        if (minPaise != null && val < minPaise) return false;
        if (maxPaise != null && val > maxPaise) return false;
        return true;
      });
    }

    return rows;
  }, [rawBreakdown, amountCol, minAmount, maxAmount]);

  // Aggregate totals
  const totals = useMemo(() => {
    return filteredData.reduce(
      (acc, r) => {
        acc.transactions += r.total_transactions;
        acc.cash += r.cash_paise;
        acc.upi += r.upi_paise;
        acc.card += r.card_paise;
        acc.other += r.other_paise;
        acc.discount += r.total_discount_paise;
        acc.gross += r.total_sales_paise;
        acc.net += Math.max(0, r.total_sales_paise - r.total_discount_paise);
        return acc;
      },
      { transactions: 0, cash: 0, upi: 0, card: 0, other: 0, discount: 0, gross: 0, net: 0 }
    );
  }, [filteredData]);

  const handleReset = () => {
    setReportType('daily');
    setFromDate(firstOfMonthStr);
    setToDate(todayStr);
    setAmountCol('none');
    setMinAmount('');
    setMaxAmount('');
  };

  const handleExportCsv = () => {
    if (!filteredData.length) return;
    const headers = ['Period', 'Transactions', 'Cash (Rs)', 'UPI (Rs)', 'Card (Rs)', 'Other (Rs)', 'Discounts (Rs)', 'Gross Sales (Rs)', 'Net Sales (Rs)'];
    const rows = filteredData.map((r) => [
      r.period,
      r.total_transactions,
      (r.cash_paise / 100).toFixed(2),
      (r.upi_paise / 100).toFixed(2),
      (r.card_paise / 100).toFixed(2),
      (r.other_paise / 100).toFixed(2),
      (r.total_discount_paise / 100).toFixed(2),
      (r.total_sales_paise / 100).toFixed(2),
      ((r.total_sales_paise - r.total_discount_paise) / 100).toFixed(2),
    ]);

    const csvContent = [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `sales_report_${reportType}_${fromDate}_to_${toDate}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const columns = [
    {
      key: 'period',
      label: reportType === 'daily' ? 'Date' : reportType === 'monthly' ? 'Month' : 'Year',
      className: 'font-mono font-semibold text-slate-200',
    },
    {
      key: 'total_transactions',
      label: 'Transactions',
      headerClassName: 'text-center',
      className: 'text-center font-mono text-slate-300',
    },
    {
      key: 'cash_paise',
      label: 'Cash',
      headerClassName: 'text-right',
      className: 'text-right font-mono text-emerald-400',
      render: (r) => formatPaise(r.cash_paise),
    },
    {
      key: 'upi_paise',
      label: 'UPI',
      headerClassName: 'text-right',
      className: 'text-right font-mono text-sky-400',
      render: (r) => formatPaise(r.upi_paise),
    },
    {
      key: 'card_paise',
      label: 'Card',
      headerClassName: 'text-right',
      className: 'text-right font-mono text-indigo-400',
      render: (r) => formatPaise(r.card_paise),
    },
    {
      key: 'total_discount_paise',
      label: 'Discounts',
      headerClassName: 'text-right',
      className: 'text-right font-mono text-rose-400/80',
      render: (r) => formatPaise(r.total_discount_paise),
    },
    {
      key: 'total_sales_paise',
      label: 'Gross Sales',
      headerClassName: 'text-right',
      className: 'text-right font-mono text-slate-200 font-bold',
      render: (r) => formatPaise(r.total_sales_paise),
    },
    {
      key: 'net_sales',
      label: 'Net Sales',
      headerClassName: 'text-right',
      className: 'text-right font-mono text-emerald-400 font-bold',
      render: (r) => formatPaise(Math.max(0, r.total_sales_paise - r.total_discount_paise)),
    },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
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
          <button
            onClick={() => refetch()}
            className="p-1.5 rounded-lg border border-slate-800 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            title="Refresh Report"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
          <button
            onClick={handleExportCsv}
            disabled={!filteredData.length}
            className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs flex items-center gap-1.5 border border-slate-700/60 transition-all disabled:opacity-40"
          >
            <Download className="w-3.5 h-3.5" />
            Export CSV
          </button>
        </div>
      </div>

      {/* Control Filter Bar (Mirrors Java SalesReportsController) */}
      <div className="px-5 py-3 border-b border-slate-800/80 bg-slate-900/30 flex items-center gap-3 flex-wrap text-xs">
        {/* Report Type Selector */}
        <div className="flex items-center gap-1.5">
          <span className="text-slate-400 font-medium">Type:</span>
          <select
            value={reportType}
            onChange={(e) => setReportType(e.target.value)}
            className="bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2.5 py-1.5 focus:border-emerald-500 focus:outline-none"
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
          <span className="text-slate-400 font-medium">Amount Filter:</span>
          <select
            value={amountCol}
            onChange={(e) => setAmountCol(e.target.value)}
            className="bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2 py-1.5 focus:border-emerald-500 focus:outline-none"
          >
            <option value="none">None</option>
            <option value="gross">Gross Sales</option>
            <option value="cash">Cash</option>
            <option value="upi">UPI</option>
            <option value="card">Card</option>
          </select>
        </div>

        {amountCol !== 'none' && (
          <div className="flex items-center gap-1.5">
            <input
              type="number"
              placeholder="Min ₹"
              value={minAmount}
              onChange={(e) => setMinAmount(e.target.value)}
              className="w-20 bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2 py-1 font-mono focus:border-emerald-500 focus:outline-none"
            />
            <span className="text-slate-600">-</span>
            <input
              type="number"
              placeholder="Max ₹"
              value={maxAmount}
              onChange={(e) => setMaxAmount(e.target.value)}
              className="w-20 bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2 py-1 font-mono focus:border-emerald-500 focus:outline-none"
            />
          </div>
        )}

        <button
          onClick={handleReset}
          className="text-slate-400 hover:text-white px-2.5 py-1 rounded-lg hover:bg-slate-800 transition-colors ml-auto"
        >
          Reset
        </button>
      </div>

      {/* Summary KPI Cards Bar */}
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

      {/* Main Breakdown Table */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          data={filteredData}
          keyFn={(r) => r.period}
          searchKeys={['period']}
          pageSize={25}
          emptyMessage="No sales recorded for the selected period."
          isLoading={isLoading}
        />
      </div>
    </div>
  );
}
