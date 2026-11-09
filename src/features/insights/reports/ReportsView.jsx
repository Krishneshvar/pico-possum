import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  BarChart3,
  TrendingUp,
  CreditCard,
  Banknote,
  QrCode,
  Flame,
  Calendar,
  RefreshCw,
  FolderTree,
  RotateCcw,
  X,
  Filter,
  Receipt,
  Tag,
  Gift,
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise } from '../../../lib/formatters';
import { nextSortState, sortRows } from '../../../lib/sort';
import SortableTh from '../../../components/common/SortableTh';
import MultiYearComparisonChart from './MultiYearComparisonChart';
import Select from '../../../components/common/Select';
import DatePicker from '../../../components/common/DatePicker';

export default function ReportsView() {
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('ALL'); // 'ALL' | 'CASH' | 'UPI' | 'CARD' | 'GIFT_CARD'
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

  // Sales list for computing counts
  const { data: allSales = [] } = useQuery({
    queryKey: ['sales'],
    queryFn: () => tauriApi.getSales(1000, 0, null),
  });

  const pmCounts = useMemo(() => {
    const counts = { ALL: 0, CASH: 0, UPI: 0, CARD: 0, GIFT_CARD: 0 };
    for (const s of allSales) {
      if (s.status === 'CANCELLED') continue;
      if (fromDate && s.created_at && s.created_at.split('T')[0] < fromDate) continue;
      if (toDate && s.created_at && s.created_at.split('T')[0] > toDate) continue;
      counts.ALL++;
      const pm = (s.payment_method || 'CASH').toUpperCase();
      if (pm === 'GIFT_CARD' || pm === 'GIFT CARD' || pm === 'GIFTCARD' || pm === 'GC' || pm === 'X') {
        counts.GIFT_CARD++;
      } else if (counts[pm] !== undefined) {
        counts[pm]++;
      } else {
        counts.GIFT_CARD++;
      }
    }
    return counts;
  }, [allSales, fromDate, toDate]);

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
  const giftCardRev = summary?.gift_card_revenue_paise || 0;
  const totalCount = summary?.total_sales_count || 0;
  const avgBill = totalCount > 0 ? Math.round(totalRev / totalCount) : 0;
  const totalDiscount = summary?.total_discount_paise || 0;

  const cashPct = totalRev > 0 ? (cashRev / totalRev) * 100 : 0;
  const upiPct = totalRev > 0 ? (upiRev / totalRev) * 100 : 0;
  const cardPct = totalRev > 0 ? (cardRev / totalRev) * 100 : 0;
  const giftCardPct = totalRev > 0 ? (giftCardRev / totalRev) * 100 : 0;

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

  const handleResetFilters = () => {
    setFromDate('');
    setToDate('');
    setPaymentMethod('ALL');
    setYearsBack(3);
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas text-content overflow-hidden">
      {/* 1. Header Toolbar */}
      <div className="h-14 px-6 border-b border-border bg-surface flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-brand-subtle text-brand flex items-center justify-center border border-brand/20">
            <BarChart3 className="w-4 h-4" />
          </div>
          <div>
            <h1 className="text-sm font-semibold text-content">Sales Analytics</h1>
            <p className="text-xs text-content-secondary">
              Revenue summaries, growth comparisons, and categorical breakdown
            </p>
          </div>
        </div>

        {/* Global Reset and Refresh Controls */}
        <div className="flex items-center gap-2">
          <button
            onClick={handleResetFilters}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface border border-border hover:bg-surface-secondary text-content-secondary hover:text-content text-xs font-medium transition-colors"
            title="Reset filters"
          >
            <RotateCcw className="w-3.5 h-3.5 text-content-muted" />
            <span>Reset</span>
          </button>
          <button
            onClick={refetchAll}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface border border-border hover:bg-surface-secondary text-content-secondary hover:text-content text-xs font-medium transition-colors"
            title="Refresh analytics data"
          >
            <RefreshCw className="w-3.5 h-3.5 text-brand" />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Main Content Area */}
      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        {/* 2. Filter Toolbar */}
        <div className="flex items-center justify-between bg-surface p-3.5 rounded-xl border border-border flex-wrap gap-3">
          {/* Date Pickers */}
          <div className="flex items-center gap-2 text-xs text-content-secondary flex-wrap">
            <span className="font-medium text-content">Reporting Period:</span>
            <DatePicker
              value={fromDate}
              onChange={(val) => setFromDate(val)}
              placeholder="From date"
            />
            <span className="text-content-muted">to</span>
            <DatePicker
              value={toDate}
              onChange={(val) => setToDate(val)}
              placeholder="To date"
            />

            {/* Quick Date Presets */}
            <div className="flex items-center gap-1 ml-1">
              <button
                type="button"
                onClick={setRangeToday}
                className="px-2.5 py-1 rounded-lg bg-surface-secondary hover:bg-surface-tertiary border border-border text-content-secondary hover:text-content font-medium transition-colors text-[11px]"
              >
                Today
              </button>
              <button
                type="button"
                onClick={setRangeMonth}
                className="px-2.5 py-1 rounded-lg bg-surface-secondary hover:bg-surface-tertiary border border-border text-content-secondary hover:text-content font-medium transition-colors text-[11px]"
              >
                This Month
              </button>
              <button
                type="button"
                onClick={() => {
                  setFromDate('');
                  setToDate('');
                }}
                className="px-2.5 py-1 rounded-lg bg-surface-secondary hover:bg-surface-tertiary border border-border text-content-secondary hover:text-content font-medium transition-colors text-[11px]"
              >
                All Time
              </button>
            </div>
          </div>

          {/* Payment Method Filter Dropdown */}
          <div className="flex items-center gap-2 text-xs">
            <span className="text-content-secondary font-medium flex items-center gap-1.5">
              <Filter className="w-3.5 h-3.5 text-content-muted" />
              <span>Payment Method:</span>
            </span>
            <Select
              value={paymentMethod}
              onChange={(val) => setPaymentMethod(val)}
              options={[
                { value: 'ALL', label: `All Methods (${pmCounts.ALL})` },
                { value: 'CASH', label: `Cash (${pmCounts.CASH})` },
                { value: 'UPI', label: `UPI / QR (${pmCounts.UPI})` },
                { value: 'CARD', label: `Card (${pmCounts.CARD})` },
                { value: 'GIFT_CARD', label: `Gift Card (${pmCounts.GIFT_CARD})` },
              ]}
            />
          </div>
        </div>

        {/* Filter-Based View Banner (When payment method is restricted) */}
        {paymentMethod !== 'ALL' && (
          <div className="flex items-center justify-between bg-brand-subtle border border-brand/30 rounded-xl px-4 py-2.5 text-xs text-brand">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-brand" />
              <span>
                Filtered by payment method: <strong className="font-semibold uppercase">{paymentMethod.replace(/_/g, ' ')}</strong>.
                Method distribution breakdown is scoped to this selection.
              </span>
            </div>
            <button
              onClick={() => setPaymentMethod('ALL')}
              className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-surface border border-brand/40 hover:bg-surface-secondary text-brand font-medium transition-colors"
            >
              <X className="w-3 h-3" />
              <span>Show All Methods</span>
            </button>
          </div>
        )}

        {/* 3. Compact KPI Cards (Solid neutral cards with semantic accents) */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Revenue */}
          <div className="p-4 rounded-xl bg-surface border border-border space-y-1.5">
            <div className="flex items-center justify-between text-xs text-content-secondary">
              <span className="font-medium">Total Revenue</span>
              <div className="w-7 h-7 rounded-lg bg-brand-subtle text-brand flex items-center justify-center">
                <TrendingUp className="w-3.5 h-3.5" />
              </div>
            </div>
            <div className="text-2xl font-semibold text-content financial-value">
              {formatPaise(totalRev)}
            </div>
            <p className="text-[11px] text-content-muted">
              {paymentMethod === 'ALL' ? 'Gross transaction volume' : `${paymentMethod.replace(/_/g, ' ')} volume`}
            </p>
          </div>

          {/* Bills Issued */}
          <div className="p-4 rounded-xl bg-surface border border-border space-y-1.5">
            <div className="flex items-center justify-between text-xs text-content-secondary">
              <span className="font-medium">Total Invoices</span>
              <div className="w-7 h-7 rounded-lg bg-surface-secondary text-content-secondary flex items-center justify-center border border-border">
                <Receipt className="w-3.5 h-3.5" />
              </div>
            </div>
            <div className="text-2xl font-semibold text-content financial-value">
              {totalCount}
            </div>
            <p className="text-[11px] text-content-muted">Customer transactions</p>
          </div>

          {/* Average Bill Value */}
          <div className="p-4 rounded-xl bg-surface border border-border space-y-1.5">
            <div className="flex items-center justify-between text-xs text-content-secondary">
              <span className="font-medium">Average Bill Value</span>
              <div className="w-7 h-7 rounded-lg bg-surface-secondary text-content-secondary flex items-center justify-center border border-border">
                <BarChart3 className="w-3.5 h-3.5" />
              </div>
            </div>
            <div className="text-2xl font-semibold text-content financial-value">
              {formatPaise(avgBill)}
            </div>
            <p className="text-[11px] text-content-muted">Per checkout average</p>
          </div>

          {/* Discounts Given */}
          <div className="p-4 rounded-xl bg-surface border border-border space-y-1.5">
            <div className="flex items-center justify-between text-xs text-content-secondary">
              <span className="font-medium">Total Discounts</span>
              <div className="w-7 h-7 rounded-lg bg-warning/10 text-warning flex items-center justify-center">
                <Tag className="w-3.5 h-3.5" />
              </div>
            </div>
            <div className="text-2xl font-semibold text-content financial-value">
              {formatPaise(totalDiscount)}
            </div>
            <p className="text-[11px] text-content-muted">Customer savings awarded</p>
          </div>
        </div>

        {/* 4. Multi-Year Comparison Chart */}
        <MultiYearComparisonChart
          data={multiYearData}
          yearsBack={yearsBack}
          onYearsBackChange={setYearsBack}
          isLoading={loadingMultiYear}
          paymentMethod={paymentMethod}
        />

        {/* 5. Payment Distribution Bar: Show BOTH absolute ₹ values and percentages */}
        {paymentMethod === 'ALL' && (
          <div className="p-5 rounded-xl bg-surface border border-border space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-sm font-semibold text-content">Revenue by Payment Method</h2>
                <p className="text-xs text-content-secondary">Distribution across payment collection channels</p>
              </div>
              <span className="text-xs font-semibold text-content financial-value">
                Total: {formatPaise(totalRev)}
              </span>
            </div>

            {/* Visual Stacked Distribution Bar */}
            {totalRev > 0 && (
              <div className="space-y-2">
                <div className="w-full h-3 bg-surface-secondary rounded-full overflow-hidden flex border border-border">
                  {cashPct > 0 && (
                    <div
                      style={{ width: `${cashPct}%`, backgroundColor: '#2563EB' }}
                      className="h-full transition-all"
                      title={`Cash: ${formatPaise(cashRev)} (${cashPct.toFixed(1)}%)`}
                    />
                  )}
                  {upiPct > 0 && (
                    <div
                      style={{ width: `${upiPct}%`, backgroundColor: '#0F766E' }}
                      className="h-full transition-all"
                      title={`UPI / QR: ${formatPaise(upiRev)} (${upiPct.toFixed(1)}%)`}
                    />
                  )}
                  {cardPct > 0 && (
                    <div
                      style={{ width: `${cardPct}%`, backgroundColor: '#7C3AED' }}
                      className="h-full transition-all"
                      title={`Card: ${formatPaise(cardRev)} (${cardPct.toFixed(1)}%)`}
                    />
                  )}
                  {giftCardPct > 0 && (
                    <div
                      style={{ width: `${giftCardPct}%`, backgroundColor: '#D97706' }}
                      className="h-full transition-all"
                      title={`Gift Card: ${formatPaise(giftCardRev)} (${giftCardPct.toFixed(1)}%)`}
                    />
                  )}
                </div>

                {/* Legend with BOTH absolute ₹ and % */}
                <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-content-secondary pt-1">
                  <span className="flex items-center gap-1.5">
                    <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: '#2563EB' }} />
                    <span className="font-medium text-content">Cash:</span>
                    <span className="financial-value">{formatPaise(cashRev)}</span>
                    <span className="text-content-muted">({cashPct.toFixed(1)}%)</span>
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: '#0F766E' }} />
                    <span className="font-medium text-content">UPI / QR:</span>
                    <span className="financial-value">{formatPaise(upiRev)}</span>
                    <span className="text-content-muted">({upiPct.toFixed(1)}%)</span>
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: '#7C3AED' }} />
                    <span className="font-medium text-content">Card:</span>
                    <span className="financial-value">{formatPaise(cardRev)}</span>
                    <span className="text-content-muted">({cardPct.toFixed(1)}%)</span>
                  </span>
                  {giftCardPct > 0 && (
                    <span className="flex items-center gap-1.5">
                      <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: '#D97706' }} />
                      <span className="font-medium text-content">Gift Card:</span>
                      <span className="financial-value">{formatPaise(giftCardRev)}</span>
                      <span className="text-content-muted">({giftCardPct.toFixed(1)}%)</span>
                    </span>
                  )}
                </div>
              </div>
            )}

            {/* Individual Method Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-2">
              <div className="bg-surface-secondary p-3.5 rounded-lg border border-border space-y-1">
                <div className="flex items-center gap-2 text-xs font-medium text-content-secondary">
                  <Banknote className="w-4 h-4 text-brand" /> Cash
                </div>
                <div className="text-lg font-semibold text-content financial-value">
                  {formatPaise(cashRev)}
                </div>
                <div className="text-[11px] text-content-muted">
                  {cashPct.toFixed(1)}% of total revenue
                </div>
              </div>

              <div className="bg-surface-secondary p-3.5 rounded-lg border border-border space-y-1">
                <div className="flex items-center gap-2 text-xs font-medium text-content-secondary">
                  <QrCode className="w-4 h-4 text-teal-600" /> UPI / QR
                </div>
                <div className="text-lg font-semibold text-content financial-value">
                  {formatPaise(upiRev)}
                </div>
                <div className="text-[11px] text-content-muted">
                  {upiPct.toFixed(1)}% of total revenue
                </div>
              </div>

              <div className="bg-surface-secondary p-3.5 rounded-lg border border-border space-y-1">
                <div className="flex items-center gap-2 text-xs font-medium text-content-secondary">
                  <CreditCard className="w-4 h-4 text-purple-600" /> Card
                </div>
                <div className="text-lg font-semibold text-content financial-value">
                  {formatPaise(cardRev)}
                </div>
                <div className="text-[11px] text-content-muted">
                  {cardPct.toFixed(1)}% of total revenue
                </div>
              </div>

              <div className="bg-surface-secondary p-3.5 rounded-lg border border-border space-y-1">
                <div className="flex items-center gap-2 text-xs font-medium text-content-secondary">
                  <Gift className="w-4 h-4 text-amber-600" /> Gift Card
                </div>
                <div className="text-lg font-semibold text-content financial-value">
                  {formatPaise(giftCardRev)}
                </div>
                <div className="text-[11px] text-content-muted">
                  {giftCardPct.toFixed(1)}% of total revenue
                </div>
              </div>
            </div>
          </div>
        )}

        {/* 6. Bottom Tables: Top Selling Categories & Products */}
        <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
          {/* Top Categories */}
          <div className="p-5 rounded-xl bg-surface border border-border space-y-3.5 flex flex-col justify-between">
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <FolderTree className="w-4 h-4 text-brand" />
                  <h2 className="text-sm font-semibold text-content">Top Selling Categories</h2>
                </div>
              </div>

              <div className="border border-border rounded-lg overflow-hidden">
                <table className="w-full text-left border-collapse text-xs">
                  <thead className="bg-table-header-bg text-table-header-text font-semibold border-b border-table-header-border">
                    <tr className="h-10">
                      <th className="px-3 w-10 text-center">#</th>
                      <SortableTh
                        sortKey="category_name"
                        sort={topCatSort}
                        onSort={(key) => setTopCatSort((p) => nextSortState(p, key))}
                        className="px-3"
                      >
                        Category
                      </SortableTh>
                      <SortableTh
                        sortKey="distinct_products_sold"
                        sort={topCatSort}
                        onSort={(key) => setTopCatSort((p) => nextSortState(p, key))}
                        align="center"
                        className="px-3"
                      >
                        Products
                      </SortableTh>
                      <SortableTh
                        sortKey="total_quantity"
                        sort={topCatSort}
                        onSort={(key) => setTopCatSort((p) => nextSortState(p, key))}
                        align="center"
                        className="px-3"
                      >
                        Sold
                      </SortableTh>
                      <SortableTh
                        sortKey="total_revenue_paise"
                        sort={topCatSort}
                        onSort={(key) => setTopCatSort((p) => nextSortState(p, key))}
                        align="right"
                        className="px-3"
                      >
                        Total Revenue
                      </SortableTh>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {loadingTopCategories ? (
                      <tr>
                        <td colSpan="5" className="py-8 text-center text-content-muted">
                          Loading categories...
                        </td>
                      </tr>
                    ) : topCategories.length === 0 ? (
                      <tr>
                        <td colSpan="5" className="py-8 text-center text-content-muted">
                          No category sales in this period.
                        </td>
                      </tr>
                    ) : (
                      sortedTopCategories.map((c, idx) => (
                        <tr key={c.category_id || idx} className="h-11 hover:bg-surface-secondary transition-colors">
                          <td className="px-3 text-center text-content-muted text-xs">
                            {idx + 1}
                          </td>
                          <td className="px-3 font-medium text-content">
                            {c.category_name}
                          </td>
                          <td className="px-3 text-center text-content-secondary financial-value">
                            {c.distinct_products_sold}
                          </td>
                          <td className="px-3 text-center font-medium text-content financial-value">
                            {c.total_quantity}
                          </td>
                          <td className="px-3 text-right font-medium text-content financial-value">
                            {formatPaise(c.total_revenue_paise)}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Category Totals Footer */}
            {topCategories.length > 0 && (
              <div className="pt-3 border-t border-border flex items-center justify-between text-xs text-content-secondary bg-surface-secondary px-3.5 py-2 rounded-lg">
                <div>
                  <span className="text-content-muted mr-1.5">Total Sold:</span>
                  <span className="font-semibold text-content financial-value">{totalCategoryUnits}</span>
                </div>
                <div>
                  <span className="text-content-muted mr-1.5">Total Revenue:</span>
                  <span className="font-semibold text-content financial-value">{formatPaise(totalCategoryRevenue)}</span>
                </div>
              </div>
            )}
          </div>

          {/* Top Products */}
          <div className="p-5 rounded-xl bg-surface border border-border space-y-3.5 flex flex-col justify-between">
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Flame className="w-4 h-4 text-warning" />
                  <h2 className="text-sm font-semibold text-content">Top Selling Products</h2>
                </div>
              </div>

              <div className="border border-border rounded-lg overflow-hidden">
                <table className="w-full text-left border-collapse text-xs">
                  <thead className="bg-table-header-bg text-table-header-text font-semibold border-b border-table-header-border">
                    <tr className="h-10">
                      <th className="px-3 w-10 text-center">#</th>
                      <SortableTh
                        sortKey="product_name"
                        sort={topProdSort}
                        onSort={(key) => setTopProdSort((p) => nextSortState(p, key))}
                        className="px-3"
                      >
                        Product
                      </SortableTh>
                      <SortableTh
                        sortKey="total_quantity"
                        sort={topProdSort}
                        onSort={(key) => setTopProdSort((p) => nextSortState(p, key))}
                        align="center"
                        className="px-3"
                      >
                        Qty Sold
                      </SortableTh>
                      <SortableTh
                        sortKey="total_revenue_paise"
                        sort={topProdSort}
                        onSort={(key) => setTopProdSort((p) => nextSortState(p, key))}
                        align="right"
                        className="px-3"
                      >
                        Total Revenue
                      </SortableTh>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {loadingTopProducts ? (
                      <tr>
                        <td colSpan="4" className="py-8 text-center text-content-muted">
                          Loading products...
                        </td>
                      </tr>
                    ) : topProducts.length === 0 ? (
                      <tr>
                        <td colSpan="4" className="py-8 text-center text-content-muted">
                          No product sales in this period.
                        </td>
                      </tr>
                    ) : (
                      sortedTopProducts.map((p, idx) => (
                        <tr key={p.product_id || idx} className="h-11 hover:bg-surface-secondary transition-colors">
                          <td className="px-3 text-center text-content-muted text-xs">
                            {idx + 1}
                          </td>
                          <td className="px-3 text-content">
                            <div className="font-medium">{p.product_name}</div>
                            {p.sku && (
                              <div className="text-[10px] text-content-muted font-mono">
                                SKU: {p.sku}
                              </div>
                            )}
                          </td>
                          <td className="px-3 text-center font-medium text-content financial-value">
                            {p.total_quantity}
                          </td>
                          <td className="px-3 text-right font-medium text-content financial-value">
                            {formatPaise(p.total_revenue_paise)}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Product Totals Footer */}
            {topProducts.length > 0 && (
              <div className="pt-3 border-t border-border flex items-center justify-between text-xs text-content-secondary bg-surface-secondary px-3.5 py-2 rounded-lg">
                <div>
                  <span className="text-content-muted mr-1.5">Total Qty:</span>
                  <span className="font-semibold text-content financial-value">{totalProductUnits}</span>
                </div>
                <div>
                  <span className="text-content-muted mr-1.5">Total Revenue:</span>
                  <span className="font-semibold text-content financial-value">{formatPaise(totalProductRevenue)}</span>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
