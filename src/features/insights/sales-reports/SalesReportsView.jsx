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
  Filter,
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { getCurrencySymbol } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';
import Select from '../../../components/common/Select';
import DatePicker, { formatToIso } from '../../../components/common/DatePicker';
import ExportButton from '../../../components/common/ExportButton';
import { strings } from '../../../lib/strings';
import { defaultColKeys, getSalesReportColumns } from './SalesReportColumns';
import SalesReportTotalsBar from './SalesReportTotalsBar';
import { exportSalesReportCsv, exportSalesReportXlsx } from './salesReportExport';

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
    () => getSalesReportColumns(reportType),
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

  const handleExportCsv = () =>
    exportSalesReportCsv({ filteredData, visibleColumns, reportType, fromDate, toDate, todayIso });

  const handleExportXlsx = () =>
    exportSalesReportXlsx({ filteredData, visibleColumns, reportType, fromDate, toDate, todayIso });

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
            className="p-1.5 rounded-lg bg-surface hover:bg-surface-secondary text-text-secondary hover:text-text-primary transition-colors border border-border-default cursor-pointer shadow-2xs"
            title={strings.common.actions.refresh}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* ── Row 2: Filter Toolbar ── */}
      <div className="px-5 py-2.5 border-b border-border-default bg-surface flex items-center gap-3 flex-wrap shrink-0">
        <div className="w-36">
          <Select
            value={reportType}
            onChange={(val) => setReportType(val)}
            options={[
              { value: 'today', label: strings.salesReports.periodOptions.today },
              { value: 'daily', label: strings.salesReports.periodOptions.daily },
              { value: 'weekly', label: strings.salesReports.periodOptions.weekly },
              { value: 'monthly', label: strings.salesReports.periodOptions.monthly },
              { value: 'yearly', label: strings.salesReports.periodOptions.yearly },
            ]}
          />
        </div>

        {/* Date Pickers */}
        <div className="flex items-center gap-1.5">
          <DatePicker
            value={fromDate}
            onChange={(val) => setFromDate(val)}
            placeholder={strings.salesReports.filters.fromDate}
          />
          <span className="text-xs text-text-muted">{strings.salesReports.filters.dateTo}</span>
          <DatePicker
            value={toDate}
            onChange={(val) => setToDate(val)}
            placeholder={strings.salesReports.filters.toDate}
          />
        </div>

        {/* Advanced Filters Toggle */}
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
          <span className="text-text-muted font-semibold">{strings.salesReports.filters.filterThreshold}</span>
          <Select
            value={amountCol}
            onChange={(val) => setAmountCol(val)}
            options={[
              { value: 'gross', label: strings.salesReports.filters.amountFields.gross },
              { value: 'net', label: strings.salesReports.filters.amountFields.net },
              { value: 'cash', label: strings.salesReports.filters.amountFields.cash },
              { value: 'upi', label: strings.salesReports.filters.amountFields.upi },
              { value: 'card', label: strings.salesReports.filters.amountFields.card },
              { value: 'gift_card', label: strings.salesReports.filters.amountFields.giftCard },
            ]}
          />
          <div className="flex items-center gap-1.5">
            <input
              type="number"
              step="any"
              placeholder={strings.salesReports.filters.minAmountPlaceholder(currencySymbol)}
              value={minAmount}
              onChange={(e) => setMinAmount(e.target.value)}
              onInput={(e) => setMinAmount(e.target.value)}
              className="w-20 bg-surface border border-border-strong text-text-primary rounded-lg px-2 py-1 font-mono focus:border-brand focus:outline-none"
            />
            <span className="text-text-muted">{strings.salesReports.filters.rangeSeparator}</span>
            <input
              type="number"
              step="any"
              placeholder={strings.salesReports.filters.maxAmountPlaceholder(currencySymbol)}
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
                {strings.common.actions.clear}
              </button>
            )}
          </div>
        </div>
      )}

      {/* ── Main Breakdown Table ── */}
      <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
        <DataTable
          columns={visibleColumns}
          persistKey="sales-reports"
          data={filteredData}
          keyFn={(r) => r.period}
          searchKeys={['period']}
          pageSize={25}
          emptyMessage={strings.salesReports.empty.noRecords}
          isLoading={isLoading}
        />
      </div>

      {/* ── Sticky Financial Totals Bar ── */}
      <SalesReportTotalsBar
        visibleColumns={visibleColumns}
        totals={totals}
      />
    </div>
  );
}
