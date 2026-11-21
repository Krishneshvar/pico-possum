import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  BarChart3,
  TrendingUp,
  CreditCard,
  Calendar,
  RefreshCw,
  RotateCcw,
  X,
  Filter,
  Receipt,
  Tag,
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise } from '../../../lib/formatters';
import { sortRows } from '../../../lib/sort';
import MultiYearComparisonChart from './MultiYearComparisonChart';
import PaymentDistributionSection from './PaymentDistributionSection';
import TopSellingCategoriesCard from './TopSellingCategoriesCard';
import TopSellingProductsCard from './TopSellingProductsCard';
import Select from '../../../components/common/Select';
import DatePicker from '../../../components/common/DatePicker';
import { strings } from '../../../lib/strings';

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
    queryKey: ['salesReportSummary', fromDate, toDate, activePmFilter],
    queryFn: () => tauriApi.getSalesReportSummary(fromDate || null, toDate || null, activePmFilter),
  });

  // 2. Top Selling Products
  const {
    data: topProducts = [],
    isLoading: loadingTopProducts,
    refetch: refetchTopProducts,
  } = useQuery({
    queryKey: ['topProducts', fromDate, toDate, activePmFilter],
    queryFn: () => tauriApi.getTopSellingProducts(10, fromDate || null, toDate || null, activePmFilter),
  });

  // 3. Top Selling Categories
  const {
    data: topCategories = [],
    isLoading: loadingTopCategories,
    refetch: refetchTopCategories,
  } = useQuery({
    queryKey: ['topCategories', fromDate, toDate, activePmFilter],
    queryFn: () => tauriApi.getTopSellingCategories(10, fromDate || null, toDate || null, activePmFilter),
  });

  // 4. Multi-Year Sales Comparison Data
  const {
    data: multiYearData = [],
    isLoading: loadingMultiYear,
    refetch: refetchMultiYear,
  } = useQuery({
    queryKey: ['multiYearSales', yearsBack, activePmFilter],
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
      const createdAt = s.createdAt ?? s.created_at;
      if (fromDate && createdAt && createdAt.split('T')[0] < fromDate) continue;
      if (toDate && createdAt && createdAt.split('T')[0] > toDate) continue;
      counts.ALL++;
      const paymentMethod = s.paymentMethod ?? s.payment_method;
      const pm = (paymentMethod || 'CASH').toUpperCase();
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
    () => sortRows(topProducts, topProdSort, (row, key) => row[key] ?? row[key.replace(/([A-Z])/g, '_$1').toLowerCase()]),
    [topProducts, topProdSort]
  );

  // Sorted category rows
  const sortedTopCategories = useMemo(
    () => sortRows(topCategories, topCatSort, (row, key) => row[key] ?? row[key.replace(/([A-Z])/g, '_$1').toLowerCase()]),
    [topCategories, topCatSort]
  );

  // Summary computations
  const totalRev = summary?.totalRevenuePaise ?? summary?.total_revenue_paise ?? 0;
  const cashRev = summary?.cashRevenuePaise ?? summary?.cash_revenue_paise ?? 0;
  const cardRev = summary?.cardRevenuePaise ?? summary?.card_revenue_paise ?? 0;
  const upiRev = summary?.upiRevenuePaise ?? summary?.upi_revenue_paise ?? 0;
  const giftCardRev = summary?.giftCardRevenuePaise ?? summary?.gift_card_revenue_paise ?? 0;
  const totalCount = summary?.totalSalesCount ?? summary?.total_sales_count ?? 0;
  const avgBill = totalCount > 0 ? Math.round(totalRev / totalCount) : 0;
  const totalDiscount = summary?.totalDiscountPaise ?? summary?.total_discount_paise ?? 0;

  const cashPct = totalRev > 0 ? (cashRev / totalRev) * 100 : 0;
  const upiPct = totalRev > 0 ? (upiRev / totalRev) * 100 : 0;
  const cardPct = totalRev > 0 ? (cardRev / totalRev) * 100 : 0;
  const giftCardPct = totalRev > 0 ? (giftCardRev / totalRev) * 100 : 0;

  // Category totals
  const totalCategoryUnits = useMemo(
    () => topCategories.reduce((acc, c) => acc + (c.totalQuantity ?? c.total_quantity ?? 0), 0),
    [topCategories]
  );
  const totalCategoryRevenue = useMemo(
    () => topCategories.reduce((acc, c) => acc + (c.totalRevenuePaise ?? c.total_revenue_paise ?? 0), 0),
    [topCategories]
  );

  // Product totals
  const totalProductUnits = useMemo(
    () => topProducts.reduce((acc, p) => acc + (p.totalQuantity ?? p.total_quantity ?? 0), 0),
    [topProducts]
  );
  const totalProductRevenue = useMemo(
    () => topProducts.reduce((acc, p) => acc + (p.totalRevenuePaise ?? p.total_revenue_paise ?? 0), 0),
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
            <h1 className="text-sm font-semibold text-content">{strings.reports.view.title}</h1>
            <p className="text-xs text-content-secondary">
              {strings.reports.view.subtitle}
            </p>
          </div>
        </div>

        {/* Global Reset and Refresh Controls */}
        <div className="flex items-center gap-2">
          <button
            onClick={handleResetFilters}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface border border-border hover:bg-surface-secondary text-content-secondary hover:text-content text-xs font-medium transition-colors"
            title={strings.reports.view.resetFilters}
          >
            <RotateCcw className="w-3.5 h-3.5 text-content-muted" />
            <span>{strings.common.actions.reset}</span>
          </button>
          <button
            onClick={refetchAll}
            className="p-1.5 rounded-lg border border-border-default text-text-muted hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
            title={strings.reports.view.refreshData}
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Main Content Area */}
      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        {/* 2. Filter Toolbar */}
        <div className="flex items-center justify-between bg-surface p-3.5 rounded-xl border border-border flex-wrap gap-3">
          {/* Date Pickers */}
          <div className="flex items-center gap-2 text-xs text-content-secondary flex-wrap">
            <span className="font-medium text-content">{strings.reports.view.reportingPeriod}</span>
            <DatePicker
              value={fromDate}
              onChange={(val) => setFromDate(val)}
              placeholder={strings.reports.view.fromDatePlaceholder}
            />
            <span className="text-content-muted">{strings.reports.view.rangeSeparator}</span>
            <DatePicker
              value={toDate}
              onChange={(val) => setToDate(val)}
              placeholder={strings.reports.view.toDatePlaceholder}
            />

            {/* Quick Date Presets */}
            <div className="flex items-center gap-1 ml-1">
              <button
                type="button"
                onClick={setRangeToday}
                className="px-2.5 py-1 rounded-lg bg-surface-secondary hover:bg-surface-tertiary border border-border text-content-secondary hover:text-content font-medium transition-colors text-[11px]"
              >
                {strings.common.datePicker.today}
              </button>
              <button
                type="button"
                onClick={setRangeMonth}
                className="px-2.5 py-1 rounded-lg bg-surface-secondary hover:bg-surface-tertiary border border-border text-content-secondary hover:text-content font-medium transition-colors text-[11px]"
              >
                {strings.reports.view.thisMonth}
              </button>
              <button
                type="button"
                onClick={() => {
                  setFromDate('');
                  setToDate('');
                }}
                className="px-2.5 py-1 rounded-lg bg-surface-secondary hover:bg-surface-tertiary border border-border text-content-secondary hover:text-content font-medium transition-colors text-[11px]"
              >
                {strings.reports.view.allTime}
              </button>
            </div>
          </div>

          {/* Payment Method Filter Dropdown */}
          <div className="flex items-center gap-2 text-xs">
            <span className="text-content-secondary font-medium flex items-center gap-1.5">
              <Filter className="w-3.5 h-3.5 text-content-muted" />
              <span>{strings.reports.view.paymentMethod}</span>
            </span>
            <Select
              value={paymentMethod}
              onChange={(val) => setPaymentMethod(val)}
              options={[
                { value: 'ALL', label: strings.reports.view.paymentOptions.all(pmCounts.ALL) },
                { value: 'CASH', label: strings.reports.view.paymentOptions.cash(pmCounts.CASH) },
                { value: 'UPI', label: strings.reports.view.paymentOptions.upiQr(pmCounts.UPI) },
                { value: 'CARD', label: strings.reports.view.paymentOptions.card(pmCounts.CARD) },
                { value: 'GIFT_CARD', label: strings.reports.view.paymentOptions.giftCard(pmCounts.GIFT_CARD) },
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
                {strings.reports.view.filterBannerPrefix}{' '}
                <strong className="font-semibold uppercase">{paymentMethod.replace(/_/g, ' ')}</strong>
                {strings.reports.view.filterBannerSuffix}
              </span>
            </div>
            <button
              onClick={() => setPaymentMethod('ALL')}
              className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-surface border border-brand/40 hover:bg-surface-secondary text-brand font-medium transition-colors"
            >
              <X className="w-3 h-3" />
              <span>{strings.reports.view.showAllMethods}</span>
            </button>
          </div>
        )}

        {/* 3. Compact KPI Cards (Solid neutral cards with semantic accents) */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Revenue */}
          <div className="p-4 rounded-xl bg-surface border border-border space-y-1.5">
            <div className="flex items-center justify-between text-xs text-content-secondary">
              <span className="font-medium">{strings.reports.kpis.totalRevenue}</span>
              <div className="w-7 h-7 rounded-lg bg-brand-subtle text-brand flex items-center justify-center">
                <TrendingUp className="w-3.5 h-3.5" />
              </div>
            </div>
            <div className="text-2xl font-semibold text-content financial-value">
              {formatPaise(totalRev)}
            </div>
            <p className="text-[11px] text-content-muted">
              {paymentMethod === 'ALL'
                ? strings.reports.kpis.grossTransactionVolume
                : strings.reports.kpis.methodVolume(paymentMethod.replace(/_/g, ' '))}
            </p>
          </div>

          {/* Bills Issued */}
          <div className="p-4 rounded-xl bg-surface border border-border space-y-1.5">
            <div className="flex items-center justify-between text-xs text-content-secondary">
              <span className="font-medium">{strings.reports.kpis.totalInvoices}</span>
              <div className="w-7 h-7 rounded-lg bg-surface-secondary text-content-secondary flex items-center justify-center border border-border">
                <Receipt className="w-3.5 h-3.5" />
              </div>
            </div>
            <div className="text-2xl font-semibold text-content financial-value">
              {totalCount}
            </div>
            <p className="text-[11px] text-content-muted">{strings.reports.kpis.customerTransactions}</p>
          </div>

          {/* Average Bill Value */}
          <div className="p-4 rounded-xl bg-surface border border-border space-y-1.5">
            <div className="flex items-center justify-between text-xs text-content-secondary">
              <span className="font-medium">{strings.reports.kpis.averageBillValue}</span>
              <div className="w-7 h-7 rounded-lg bg-surface-secondary text-content-secondary flex items-center justify-center border border-border">
                <BarChart3 className="w-3.5 h-3.5" />
              </div>
            </div>
            <div className="text-2xl font-semibold text-content financial-value">
              {formatPaise(avgBill)}
            </div>
            <p className="text-[11px] text-content-muted">{strings.reports.kpis.perCheckoutAverage}</p>
          </div>

          {/* Discounts Given */}
          <div className="p-4 rounded-xl bg-surface border border-border space-y-1.5">
            <div className="flex items-center justify-between text-xs text-content-secondary">
              <span className="font-medium">{strings.reports.kpis.totalDiscounts}</span>
              <div className="w-7 h-7 rounded-lg bg-warning/10 text-warning flex items-center justify-center">
                <Tag className="w-3.5 h-3.5" />
              </div>
            </div>
            <div className="text-2xl font-semibold text-content financial-value">
              {formatPaise(totalDiscount)}
            </div>
            <p className="text-[11px] text-content-muted">{strings.reports.kpis.customerSavings}</p>
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

        {paymentMethod === "ALL" && (
          <PaymentDistributionSection
            totalRev={totalRev}
            cashRev={cashRev}
            upiRev={upiRev}
            cardRev={cardRev}
            giftCardRev={giftCardRev}
            cashPct={cashPct}
            upiPct={upiPct}
            cardPct={cardPct}
            giftCardPct={giftCardPct}
          />
        )}

        {/* 6. Bottom Tables: Top Selling Categories & Products */}
        <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
          <TopSellingCategoriesCard
            topCategories={topCategories}
            sortedTopCategories={sortedTopCategories}
            loadingTopCategories={loadingTopCategories}
            topCatSort={topCatSort}
            setTopCatSort={setTopCatSort}
            totalCategoryUnits={totalCategoryUnits}
            totalCategoryRevenue={totalCategoryRevenue}
          />

          <TopSellingProductsCard
            topProducts={topProducts}
            sortedTopProducts={sortedTopProducts}
            loadingTopProducts={loadingTopProducts}
            topProdSort={topProdSort}
            setTopProdSort={setTopProdSort}
            totalProductUnits={totalProductUnits}
            totalProductRevenue={totalProductRevenue}
          />
        </div>
      </div>
    </div>
  );
}
