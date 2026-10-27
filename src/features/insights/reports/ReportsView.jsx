import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  BarChart3,
  TrendingUp,
  IndianRupee,
  CreditCard,
  Banknote,
  QrCode,
  Flame,
  Calendar,
  RefreshCw,
  FolderTree,
  RotateCcw,
  X,
  Layers,
  Filter,
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise } from '../../../lib/formatters';
import { nextSortState, sortRows } from '../../../lib/sort';
import SortableTh from '../../../components/common/SortableTh';
import MultiYearComparisonChart from './MultiYearComparisonChart';

export default function ReportsView() {
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('ALL'); // 'ALL' | 'CASH' | 'UPI' | 'CARD' | 'OTHER'
  const [yearsBack, setYearsBack] = useState(3);

  // Sorting state for tables
  const [topProdSort, setTopProdSort] = useState({ key: null, dir: null });
  const [topCatSort, setTopCatSort] = useState({ key: null, dir: null });

  const activePmFilter = paymentMethod === 'ALL' ? null : paymentMethod;

  // 1. KPI Sales Summary
  const {
    data: summary,
    isLoading: loadingSummary,
    refetch: refetchSummary,
  } = useQuery({
    queryKey: ['sales_report_summary', fromDate, toDate, activePmFilter],
    queryFn: () => tauriApi.getSalesReportSummary(fromDate || null, toDate || null, activePmFilter),
  });

  // 2. Top Selling Products
  const {
    data: topProducts = [],
    isLoading: loadingTopProducts,
    refetch: refetchTopProducts,
  } = useQuery({
    queryKey: ['top_products', fromDate, toDate, activePmFilter],
    queryFn: () => tauriApi.getTopSellingProducts(10, fromDate || null, toDate || null, activePmFilter),
  });

  // 3. Top Selling Categories
  const {
    data: topCategories = [],
    isLoading: loadingTopCategories,
    refetch: refetchTopCategories,
  } = useQuery({
    queryKey: ['top_categories', fromDate, toDate, activePmFilter],
    queryFn: () => tauriApi.getTopSellingCategories(10, fromDate || null, toDate || null, activePmFilter),
  });

  // 4. Multi-Year Sales Comparison Data
  const {
    data: multiYearData = [],
    isLoading: loadingMultiYear,
    refetch: refetchMultiYear,
  } = useQuery({
    queryKey: ['multi_year_sales', yearsBack, activePmFilter],
    queryFn: () => tauriApi.getMultiYearSales(yearsBack, activePmFilter),
  });

  const refetchAll = () => {
    refetchSummary();
    refetchTopProducts();
    refetchTopCategories();
    refetchMultiYear();
  };

  // Sorted product rows
  const sortedTopProducts = useMemo(
    () => sortRows(topProducts, topProdSort, (row, key) => row[key]),
    [topProducts, topProdSort]
  );

  // Sorted category rows
  const sortedTopCategories = useMemo(
    () => sortRows(topCategories, topCatSort, (row, key) => row[key]),
    [topCategories, topCatSort]
  );

  // Summary computations
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

  // Category totals
  const totalCategoryUnits = useMemo(
    () => topCategories.reduce((acc, c) => acc + (c.total_quantity || 0), 0),
    [topCategories]
  );
  const totalCategoryRevenue = useMemo(
    () => topCategories.reduce((acc, c) => acc + (c.total_revenue_paise || 0), 0),
    [topCategories]
  );

  // Product totals
  const totalProductUnits = useMemo(
    () => topProducts.reduce((acc, p) => acc + (p.total_quantity || 0), 0),
    [topProducts]
  );
  const totalProductRevenue = useMemo(
    () => topProducts.reduce((acc, p) => acc + (p.total_revenue_paise || 0), 0),
    [topProducts]
  );

  // Quick Range Presets
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

  const handleResetFilters = () => {
    setFromDate('');
    setToDate('');
    setPaymentMethod('ALL');
    setYearsBack(3);
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* View Header */}
      <div className="h-14 px-5 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <BarChart3 className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-100">Sales Analytics</h2>
            <p className="text-xs text-slate-400">
              Financial summaries, multi-year performance, and category performance
            </p>
          </div>
        </div>

        {/* Global Reset and Refresh Controls */}
        <div className="flex items-center gap-2">
          <button
            onClick={handleResetFilters}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-300 text-xs font-semibold transition-all hover:border-slate-700 shadow-sm"
            title="Reset filters"
          >
            <RotateCcw className="w-3.5 h-3.5 text-slate-400" />
            <span>Reset</span>
          </button>
          <button
            onClick={refetchAll}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-300 text-xs font-semibold transition-all hover:border-slate-700 shadow-sm"
            title="Refresh analytics data"
          >
            <RefreshCw className="w-3.5 h-3.5 text-emerald-400" />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Main Content Area */}
      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        {/* Filter Bar */}
        <div className="flex items-center justify-between bg-slate-900/60 p-3.5 rounded-2xl border border-slate-800 flex-wrap gap-3 shadow-sm">
          {/* Date Pickers */}
          <div className="flex items-center gap-2 text-xs text-slate-400 flex-wrap">
            <Calendar className="w-4 h-4 text-emerald-400 flex-shrink-0" />
            <span className="font-medium text-slate-300">Reporting Range:</span>
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

            {/* Quick Date Presets */}
            <div className="flex items-center gap-1 ml-1">
              <button
                onClick={setRangeToday}
                className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium transition-colors text-[11px]"
              >
                Today
              </button>
              <button
                onClick={setRangeMonth}
                className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium transition-colors text-[11px]"
              >
                This Month
              </button>
              <button
                onClick={setRangeAll}
                className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium transition-colors text-[11px]"
              >
                All Time
              </button>
            </div>
          </div>

          {/* Payment Method Filter Dropdown */}
          <div className="flex items-center gap-2 text-xs">
            <span className="text-slate-400 font-medium flex items-center gap-1.5">
              <Filter className="w-3.5 h-3.5 text-cyan-400" />
              <span>Payment Method:</span>
            </span>
            <select
              value={paymentMethod}
              onChange={(e) => setPaymentMethod(e.target.value)}
              className="bg-slate-950 border border-slate-800 rounded-lg px-3 py-1 text-slate-200 font-semibold focus:border-emerald-500 focus:outline-none cursor-pointer"
            >
              <option value="ALL">All Payment Methods</option>
              <option value="CASH">Cash</option>
              <option value="UPI">UPI / QR</option>
              <option value="CARD">Card</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
        </div>

        {/* Filter-Based View Banner (When specific payment method is filtered) */}
        {paymentMethod !== 'ALL' && (
          <div className="flex items-center justify-between bg-emerald-500/10 border border-emerald-500/30 rounded-xl px-4 py-2.5 text-xs text-emerald-300">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              <span>
                Filtered by payment method: <strong className="font-mono text-emerald-200 uppercase">{paymentMethod}</strong>.
                Payment-method breakdown analytics is hidden to focus on scoped transaction performance.
              </span>
            </div>
            <button
              onClick={() => setPaymentMethod('ALL')}
              className="flex items-center gap-1 px-2.5 py-0.5 rounded-lg bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-200 font-semibold transition-colors"
            >
              <X className="w-3 h-3" />
              <span>Show All Methods</span>
            </button>
          </div>
        )}

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
            <p className="text-[11px] text-emerald-400/90 font-medium">
              {paymentMethod === 'ALL' ? 'All completed transactions' : `${paymentMethod} transactions`}
            </p>
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

        {/* Multi-Year Performance Comparison Chart */}
        <MultiYearComparisonChart
          data={multiYearData}
          yearsBack={yearsBack}
          onYearsBackChange={setYearsBack}
          isLoading={loadingMultiYear}
          paymentMethod={paymentMethod}
        />

        {/* Payment Method Breakdown Grid & Distribution Bar (Rendered ONLY when Payment Method filter is 'ALL') */}
        {paymentMethod === 'ALL' && (
          <div className="glass-card p-6 rounded-2xl border border-slate-800 space-y-4">
            <div className="flex items-center justify-between">
              <div className="space-y-0.5">
                <h3 className="text-sm font-bold text-slate-100">Revenue by Payment Method</h3>
                <p className="text-xs text-slate-400">Distribution across payment channels</p>
              </div>
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
                  <span className="flex items-center gap-1">
                    <span className="w-2 h-2 rounded-full bg-emerald-500" /> Cash ({cashPct.toFixed(1)}%)
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="w-2 h-2 rounded-full bg-cyan-500" /> UPI ({upiPct.toFixed(1)}%)
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="w-2 h-2 rounded-full bg-blue-500" /> Card ({cardPct.toFixed(1)}%)
                  </span>
                  {otherPct > 0 && (
                    <span className="flex items-center gap-1">
                      <span className="w-2 h-2 rounded-full bg-purple-500" /> Other ({otherPct.toFixed(1)}%)
                    </span>
                  )}
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
        )}

        {/* Bottom Section: Top Selling Categories & Top Selling Products */}
        <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
          {/* Top Selling Categories */}
          <div className="glass-card p-6 rounded-2xl border border-slate-800 space-y-4 flex flex-col justify-between">
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <FolderTree className="w-4 h-4 text-emerald-400" />
                  <h3 className="text-sm font-bold text-slate-100">Top Selling Categories</h3>
                </div>
                <span className="text-[11px] text-slate-400 font-mono">
                  {topCategories.length} categories
                </span>
              </div>

              <div className="border border-slate-800 rounded-xl overflow-hidden">
                <table className="w-full text-left border-collapse text-xs">
                  <thead className="bg-slate-900 text-slate-400 font-semibold uppercase text-[10px] tracking-wider border-b border-slate-800">
                    <tr>
                      <th className="py-2.5 px-3 w-10 text-center">#</th>
                      <SortableTh
                        sortKey="category_name"
                        sort={topCatSort}
                        onSort={(key) => setTopCatSort((p) => nextSortState(p, key))}
                        className="py-2.5 px-3"
                      >
                        Category
                      </SortableTh>
                      <SortableTh
                        sortKey="distinct_products_sold"
                        sort={topCatSort}
                        onSort={(key) => setTopCatSort((p) => nextSortState(p, key))}
                        align="center"
                        className="py-2.5 px-3"
                      >
                        Products
                      </SortableTh>
                      <SortableTh
                        sortKey="total_quantity"
                        sort={topCatSort}
                        onSort={(key) => setTopCatSort((p) => nextSortState(p, key))}
                        align="center"
                        className="py-2.5 px-3"
                      >
                        Sold
                      </SortableTh>
                      <SortableTh
                        sortKey="total_revenue_paise"
                        sort={topCatSort}
                        onSort={(key) => setTopCatSort((p) => nextSortState(p, key))}
                        align="right"
                        className="py-2.5 px-3"
                      >
                        Total Revenue
                      </SortableTh>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60">
                    {loadingTopCategories ? (
                      <tr>
                        <td colSpan="5" className="py-8 text-center text-slate-500 font-medium">
                          Loading categories...
                        </td>
                      </tr>
                    ) : topCategories.length === 0 ? (
                      <tr>
                        <td colSpan="5" className="py-8 text-center text-slate-500">
                          No category sales in this period.
                        </td>
                      </tr>
                    ) : (
                      sortedTopCategories.map((c, idx) => (
                        <tr key={c.category_id || idx} className="hover:bg-slate-900/40 transition-colors">
                          <td className="py-2.5 px-3 text-center text-slate-500 font-mono font-bold">
                            {idx + 1}
                          </td>
                          <td className="py-2.5 px-3 font-semibold text-slate-200">
                            {c.category_name}
                          </td>
                          <td className="py-2.5 px-3 text-center font-mono text-slate-300">
                            {c.distinct_products_sold}
                          </td>
                          <td className="py-2.5 px-3 text-center font-mono font-bold text-emerald-400">
                            {c.total_quantity}
                          </td>
                          <td className="py-2.5 px-3 text-right font-mono font-bold text-slate-100">
                            {formatPaise(c.total_revenue_paise)}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Category Summary Totals Footer */}
            {topCategories.length > 0 && (
              <div className="mt-3 pt-3 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-300 font-mono bg-slate-900/40 px-3.5 py-2 rounded-xl">
                <div>
                  <span className="text-slate-500 font-sans font-medium mr-1.5">Total Sold:</span>
                  <span className="text-emerald-400 font-bold">{totalCategoryUnits}</span>
                </div>
                <div>
                  <span className="text-slate-500 font-sans font-medium mr-1.5">Total Revenue:</span>
                  <span className="text-slate-100 font-bold">{formatPaise(totalCategoryRevenue)}</span>
                </div>
              </div>
            )}
          </div>

          {/* Top Selling Products */}
          <div className="glass-card p-6 rounded-2xl border border-slate-800 space-y-4 flex flex-col justify-between">
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Flame className="w-4 h-4 text-amber-400" />
                  <h3 className="text-sm font-bold text-slate-100">Top Selling Products</h3>
                </div>
                <span className="text-[11px] text-slate-400 font-mono">
                  {topProducts.length} products
                </span>
              </div>

              <div className="border border-slate-800 rounded-xl overflow-hidden">
                <table className="w-full text-left border-collapse text-xs">
                  <thead className="bg-slate-900 text-slate-400 font-semibold uppercase text-[10px] tracking-wider border-b border-slate-800">
                    <tr>
                      <th className="py-2.5 px-3 w-10 text-center">#</th>
                      <SortableTh
                        sortKey="product_name"
                        sort={topProdSort}
                        onSort={(key) => setTopProdSort((p) => nextSortState(p, key))}
                        className="py-2.5 px-3"
                      >
                        Product
                      </SortableTh>
                      <SortableTh
                        sortKey="total_quantity"
                        sort={topProdSort}
                        onSort={(key) => setTopProdSort((p) => nextSortState(p, key))}
                        align="center"
                        className="py-2.5 px-3"
                      >
                        Qty Sold
                      </SortableTh>
                      <SortableTh
                        sortKey="total_revenue_paise"
                        sort={topProdSort}
                        onSort={(key) => setTopProdSort((p) => nextSortState(p, key))}
                        align="right"
                        className="py-2.5 px-3"
                      >
                        Total Revenue
                      </SortableTh>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60">
                    {loadingTopProducts ? (
                      <tr>
                        <td colSpan="4" className="py-8 text-center text-slate-500 font-medium">
                          Loading products...
                        </td>
                      </tr>
                    ) : topProducts.length === 0 ? (
                      <tr>
                        <td colSpan="4" className="py-8 text-center text-slate-500">
                          No product sales in this period.
                        </td>
                      </tr>
                    ) : (
                      sortedTopProducts.map((p, idx) => (
                        <tr key={p.product_id || idx} className="hover:bg-slate-900/40 transition-colors">
                          <td className="py-2.5 px-3 text-center text-slate-500 font-mono font-bold">
                            {idx + 1}
                          </td>
                          <td className="py-2.5 px-3 font-semibold text-slate-200">
                            <div>{p.product_name}</div>
                            {p.sku && (
                              <div className="text-[10px] text-slate-500 font-mono font-normal">
                                SKU: {p.sku}
                              </div>
                            )}
                          </td>
                          <td className="py-2.5 px-3 text-center font-mono font-bold text-emerald-400">
                            {p.total_quantity}
                          </td>
                          <td className="py-2.5 px-3 text-right font-mono font-bold text-slate-100">
                            {formatPaise(p.total_revenue_paise)}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Product Summary Totals Footer */}
            {topProducts.length > 0 && (
              <div className="mt-3 pt-3 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-300 font-mono bg-slate-900/40 px-3.5 py-2 rounded-xl">
                <div>
                  <span className="text-slate-500 font-sans font-medium mr-1.5">Total Qty:</span>
                  <span className="text-emerald-400 font-bold">{totalProductUnits}</span>
                </div>
                <div>
                  <span className="text-slate-500 font-sans font-medium mr-1.5">Total Revenue:</span>
                  <span className="text-slate-100 font-bold">{formatPaise(totalProductRevenue)}</span>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
