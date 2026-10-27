import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BarChart3, TrendingUp, IndianRupee, CreditCard, Banknote, QrCode, Flame, Calendar, RefreshCw } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise, formatDateTime } from '../../../lib/formatters';
import { nextSortState, sortRows } from '../../../lib/sort';
import DataTable from '../../../components/common/DataTable';
import SortableTh from '../../../components/common/SortableTh';

export default function ReportsView() {
  const [activeTab, setActiveTab] = useState('summary'); // 'summary' | 'audit'
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [topSort, setTopSort] = useState({ key: null, dir: null });

  const { data: summary, isLoading: loadingSummary, refetch } = useQuery({
    queryKey: ['sales_report_summary', fromDate, toDate],
    queryFn: () => tauriApi.getSalesReportSummary(fromDate || null, toDate || null),
  });

  const { data: topProducts = [], isLoading: loadingTop } = useQuery({
    queryKey: ['top_products'],
    queryFn: () => tauriApi.getTopSellingProducts(10),
  });

  const sortedTopProducts = useMemo(
    () => sortRows(topProducts, topSort, (row, key) => row[key]),
    [topProducts, topSort]
  );

  const { data: auditLogs = [], isLoading: loadingAudit } = useQuery({
    queryKey: ['audit_logs'],
    queryFn: () => tauriApi.getAuditLogs(100),
    enabled: activeTab === 'audit',
  });

  const totalRev = summary?.total_revenue_paise || 0;
  const cashRev = summary?.cash_revenue_paise || 0;
  const cardRev = summary?.card_revenue_paise || 0;
  const upiRev = summary?.upi_revenue_paise || 0;
  const otherRev = summary?.other_revenue_paise || 0;
  const totalCount = summary?.total_sales_count || 0;
  const avgBill = totalCount > 0 ? Math.round(totalRev / totalCount) : 0;

  const cashPct = totalRev > 0 ? (cashRev / totalRev) * 100 : 0;
  const upiPct = totalRev > 0 ? (upiRev / totalRev) * 100 : 0;
  const cardPct = totalRev > 0 ? (cardRev / totalRev) * 100 : 0;
  const otherPct = totalRev > 0 ? (otherRev / totalRev) * 100 : 0;

  const setRangeToday = () => {
    const today = new Date().toISOString().split('T')[0];
    setFromDate(today);
    setToDate(today);
  };

  const setRangeMonth = () => {
    const now = new Date();
    const firstDay = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
    const today = now.toISOString().split('T')[0];
    setFromDate(firstDay);
    setToDate(today);
  };

  const setRangeAll = () => {
    setFromDate('');
    setToDate('');
  };

  const auditColumns = [
    { key: 'created_at', label: 'Timestamp', className: 'font-mono text-slate-400 text-[11px] whitespace-nowrap', render: (r) => formatDateTime(r.created_at) },
    { key: 'event_type', label: 'Event', className: 'font-mono font-bold text-emerald-400' },
    { key: 'entity_type', label: 'Module', className: 'text-slate-300 uppercase text-[11px] font-mono' },
    { key: 'description', label: 'Description', className: 'text-slate-300' },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-14 px-5 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <BarChart3 className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-100">Analytics & Reports</h2>
            <p className="text-xs text-slate-400">Financial summaries, product performance, and system audit trail</p>
          </div>
        </div>

        {/* Tab Toggle */}
        <div className="flex bg-slate-900 border border-slate-800 rounded-xl p-1 text-xs">
          <button
            onClick={() => setActiveTab('summary')}
            className={`px-3 py-1.5 rounded-lg font-semibold transition-all ${
              activeTab === 'summary' ? 'bg-emerald-500 text-slate-950 shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            Sales Overview
          </button>
          <button
            onClick={() => setActiveTab('audit')}
            className={`px-3 py-1.5 rounded-lg font-semibold transition-all ${
              activeTab === 'audit' ? 'bg-emerald-500 text-slate-950 shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            System Audit Trail
          </button>
        </div>
      </div>

      {activeTab === 'summary' ? (
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Date Filter Toolbar */}
          <div className="flex items-center justify-between bg-slate-900/60 p-3 rounded-xl border border-slate-800 flex-wrap gap-3">
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <Calendar className="w-4 h-4 text-emerald-400" />
              <span>Reporting Range:</span>
              <input
                type="date"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
                className="bg-slate-950 border border-slate-800 rounded-lg px-2 py-1 text-slate-200 text-xs font-mono focus:border-emerald-500 focus:outline-none"
              />
              <span>to</span>
              <input
                type="date"
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
                className="bg-slate-950 border border-slate-800 rounded-lg px-2 py-1 text-slate-200 text-xs font-mono focus:border-emerald-500 focus:outline-none"
              />
            </div>
            <div className="flex items-center gap-1.5 text-xs">
              <button
                onClick={setRangeToday}
                className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium transition-colors"
              >
                Today
              </button>
              <button
                onClick={setRangeMonth}
                className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium transition-colors"
              >
                This Month
              </button>
              <button
                onClick={setRangeAll}
                className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium transition-colors"
              >
                All Time
              </button>
              <button
                onClick={() => refetch()}
                className="p-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white transition-colors"
                title="Refresh"
              >
                <RefreshCw className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          {/* Top Metric Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="glass-card p-5 rounded-2xl border border-slate-800 space-y-2">
              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>Total Revenue</span>
                <span className="p-1.5 rounded-lg bg-emerald-500/10 text-emerald-400">
                  <TrendingUp className="w-4 h-4" />
                </span>
              </div>
              <div className="text-2xl font-black text-slate-100 font-mono">
                {formatPaise(totalRev)}
              </div>
              <p className="text-[11px] text-emerald-400/90 font-medium">All completed transactions</p>
            </div>

            <div className="glass-card p-5 rounded-2xl border border-slate-800 space-y-2">
              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>Total Bills Issued</span>
                <span className="p-1.5 rounded-lg bg-teal-500/10 text-teal-400">
                  <BarChart3 className="w-4 h-4" />
                </span>
              </div>
              <div className="text-2xl font-black text-slate-100 font-mono">
                {totalCount}
              </div>
              <p className="text-[11px] text-slate-400 font-medium">Customer checkouts</p>
            </div>

            <div className="glass-card p-5 rounded-2xl border border-slate-800 space-y-2">
              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>Average Bill Value</span>
                <span className="p-1.5 rounded-lg bg-cyan-500/10 text-cyan-400">
                  <IndianRupee className="w-4 h-4" />
                </span>
              </div>
              <div className="text-2xl font-black text-slate-100 font-mono">
                {formatPaise(avgBill)}
              </div>
              <p className="text-[11px] text-slate-400 font-medium">Per checkout average</p>
            </div>

            <div className="glass-card p-5 rounded-2xl border border-slate-800 space-y-2">
              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>Total Discounts Given</span>
                <span className="p-1.5 rounded-lg bg-amber-500/10 text-amber-400">
                  <TrendingUp className="w-4 h-4" />
                </span>
              </div>
              <div className="text-2xl font-black text-amber-400 font-mono">
                {formatPaise(summary?.total_discount_paise || 0)}
              </div>
              <p className="text-[11px] text-slate-400 font-medium">Customer savings</p>
            </div>
          </div>

          {/* Payment Method Breakdown Grid & Distribution Bar */}
          <div className="glass-card p-6 rounded-2xl border border-slate-800 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-100">Revenue by Payment Method</h3>
              <span className="text-xs text-slate-400 font-mono">Total: {formatPaise(totalRev)}</span>
            </div>

            {/* Visual Stacked Distribution Bar */}
            {totalRev > 0 && (
              <div className="space-y-1.5">
                <div className="w-full h-3.5 bg-slate-900 rounded-full overflow-hidden flex border border-slate-800 shadow-inner">
                  {cashPct > 0 && (
                    <div
                      style={{ width: `${cashPct}%` }}
                      className="bg-emerald-500 h-full transition-all hover:opacity-90"
                      title={`Cash: ${cashPct.toFixed(1)}%`}
                    />
                  )}
                  {upiPct > 0 && (
                    <div
                      style={{ width: `${upiPct}%` }}
                      className="bg-cyan-500 h-full transition-all hover:opacity-90"
                      title={`UPI: ${upiPct.toFixed(1)}%`}
                    />
                  )}
                  {cardPct > 0 && (
                    <div
                      style={{ width: `${cardPct}%` }}
                      className="bg-blue-500 h-full transition-all hover:opacity-90"
                      title={`Card: ${cardPct.toFixed(1)}%`}
                    />
                  )}
                  {otherPct > 0 && (
                    <div
                      style={{ width: `${otherPct}%` }}
                      className="bg-purple-500 h-full transition-all hover:opacity-90"
                      title={`Other: ${otherPct.toFixed(1)}%`}
                    />
                  )}
                </div>
                <div className="flex items-center justify-end gap-4 text-[10px] text-slate-400">
                  <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-emerald-500" /> Cash ({cashPct.toFixed(1)}%)</span>
                  <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-cyan-500" /> UPI ({upiPct.toFixed(1)}%)</span>
                  <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-blue-500" /> Card ({cardPct.toFixed(1)}%)</span>
                </div>
              </div>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-1">
              <div className="bg-slate-950/60 p-4 rounded-xl border border-slate-800/80 space-y-1">
                <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
                  <Banknote className="w-4 h-4 text-emerald-400" /> Cash
                </div>
                <div className="text-xl font-bold font-mono text-slate-100">
                  {formatPaise(cashRev)}
                </div>
                <div className="text-[11px] text-slate-500">
                  {cashPct.toFixed(1)}% of revenue
                </div>
              </div>

              <div className="bg-slate-950/60 p-4 rounded-xl border border-slate-800/80 space-y-1">
                <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
                  <QrCode className="w-4 h-4 text-cyan-400" /> UPI / QR
                </div>
                <div className="text-xl font-bold font-mono text-slate-100">
                  {formatPaise(upiRev)}
                </div>
                <div className="text-[11px] text-slate-500">
                  {upiPct.toFixed(1)}% of revenue
                </div>
              </div>

              <div className="bg-slate-950/60 p-4 rounded-xl border border-slate-800/80 space-y-1">
                <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
                  <CreditCard className="w-4 h-4 text-blue-400" /> Card
                </div>
                <div className="text-xl font-bold font-mono text-slate-100">
                  {formatPaise(cardRev)}
                </div>
                <div className="text-[11px] text-slate-500">
                  {cardPct.toFixed(1)}% of revenue
                </div>
              </div>
            </div>
          </div>

          {/* Top Selling Products */}
          <div className="glass-card p-6 rounded-2xl border border-slate-800 space-y-4">
            <div className="flex items-center gap-2">
              <Flame className="w-4 h-4 text-amber-400" />
              <h3 className="text-sm font-bold text-slate-100">Top Selling Products</h3>
            </div>

            <div className="border border-slate-800 rounded-xl overflow-hidden">
              <table className="w-full text-left border-collapse text-xs">
                <thead className="bg-slate-900 text-slate-400 font-semibold uppercase text-[10px] tracking-wider border-b border-slate-800">
                  <tr>
                    <th className="py-2.5 px-4 w-12 text-center">#</th>
                    <SortableTh sortKey="product_name" sort={topSort} onSort={(key) => setTopSort((p) => nextSortState(p, key))} className="py-2.5 px-4">Product Name</SortableTh>
                    <SortableTh sortKey="total_quantity" sort={topSort} onSort={(key) => setTopSort((p) => nextSortState(p, key))} align="center" className="py-2.5 px-4">Units Sold</SortableTh>
                    <SortableTh sortKey="total_revenue_paise" sort={topSort} onSort={(key) => setTopSort((p) => nextSortState(p, key))} align="right" className="py-2.5 px-4">Total Revenue</SortableTh>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {topProducts.length === 0 ? (
                    <tr>
                      <td colSpan="4" className="py-8 text-center text-slate-500">
                        No sales data available for top products.
                      </td>
                    </tr>
                  ) : (
                    sortedTopProducts.map((p, idx) => (
                      <tr key={p.product_id || idx} className="hover:bg-slate-900/40 transition-colors">
                        <td className="py-2.5 px-4 text-center text-slate-500 font-mono font-bold">
                          {idx + 1}
                        </td>
                        <td className="py-2.5 px-4 font-semibold text-slate-200">
                          {p.product_name}
                        </td>
                        <td className="py-2.5 px-4 text-center font-mono font-bold text-emerald-400">
                          {p.total_quantity}
                        </td>
                        <td className="py-2.5 px-4 text-right font-mono font-bold text-slate-100">
                          {formatPaise(p.total_revenue_paise)}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      ) : (
        /* Audit Trail View */
        <div className="flex-1 overflow-hidden">
          <DataTable
            columns={auditColumns}
            persistKey="reports-audit"
            data={auditLogs}
            keyFn={(r) => r.id}
            searchKeys={['event_type', 'entity_type', 'description']}
            filters={[
              {
                type: 'multiselect',
                key: 'event_type',
                label: 'Actions',
                options: [
                  { value: 'SALE_COMPLETED', label: 'Sale Completed' },
                  { value: 'SALE_CANCELLED', label: 'Sale Cancelled' },
                  { value: 'STOCK_ADJUSTMENT', label: 'Stock Adjustment' },
                  { value: 'LEGACY_MIGRATION', label: 'Legacy Migration' },
                ],
              },
            ]}
            isLoading={loadingAudit}
            emptyMessage="No audit log entries found."
          />
        </div>
      )}
    </div>
  );
}
