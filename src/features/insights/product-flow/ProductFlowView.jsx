import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  History,
  Receipt,
  User,
  ArrowDownRight,
  ArrowUpRight,
  Package,
  RotateCcw,
  RefreshCw,
  ExternalLink,
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise, formatDateTime, getCurrencySymbol } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';
import Select from '../../../components/common/Select';
import DatePicker from '../../../components/common/DatePicker';
import ExportButton from '../../../components/common/ExportButton';
import { useWorkspaceStore } from '../../../stores/workspaceStore';
import { strings } from '../../../lib/strings';
import ProductFlowStats from './ProductFlowStats';
import { exportProductFlowCsv, exportProductFlowXlsx } from './productFlowExport';

export default function ProductFlowView() {
  const { openOrFocus } = useWorkspaceStore();
  const [selectedProductId, setSelectedProductId] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  const { data: generalSettings } = useQuery({
    queryKey: ['general_settings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });
  const currencySymbol = generalSettings?.currency_symbol || getCurrencySymbol();

  const { data: products = [] } = useQuery({
    queryKey: ['products'],
    queryFn: () => tauriApi.getProducts(null, null),
  });

  const selectedProduct = useMemo(
    () => products.find((p) => p.id === selectedProductId) || null,
    [products, selectedProductId]
  );

  const hasSelectedProduct = Boolean(selectedProductId);

  const { data: flowItems = [], isLoading, refetch } = useQuery({
    queryKey: ['product_flow', selectedProductId, fromDate, toDate],
    queryFn: () =>
      tauriApi.getProductFlow(selectedProductId || null, fromDate || null, toDate || null, 10000),
    enabled: hasSelectedProduct,
  });

  // Calculate Product Summary Metrics from actual recorded sales transactions
  const summaryMetrics = useMemo(() => {
    if (!flowItems.length) {
      return {
        unitsSold: 0,
        totalRevenue: 0,
        avgPrice: 0,
      };
    }
    const salesOnly = flowItems.filter((f) => f.quantity < 0 || f.event_type === 'SALE');
    const itemsToCount = salesOnly.length > 0 ? salesOnly : flowItems;
    const unitsSold = itemsToCount.reduce((acc, r) => acc + Math.abs(r.quantity || 0), 0);
    const totalRevenue = itemsToCount.reduce((acc, r) => acc + (r.total_paise || 0), 0);
    const avgPrice = unitsSold > 0 ? Math.round(totalRevenue / unitsSold) : 0;

    return {
      unitsSold,
      totalRevenue,
      avgPrice,
    };
  }, [flowItems]);

  const columns = [
    {
      key: 'created_at',
      label: strings.common.labels.dateTime,
      className: 'text-content-secondary text-xs whitespace-nowrap',
      render: (r) => formatDateTime(r.created_at),
    },
    {
      key: 'invoice_number',
      label: strings.productFlow.columns.invoice,
      className: 'font-mono text-content font-medium',
      render: (r) => {
        if (!r.invoice_number) return <span className="text-content-muted">{strings.common.states.emptyValue}</span>;
        return (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              openOrFocus(
                strings.productFlow.productFlow.billTabTitle(r.invoice_number),
                'bill-detail',
                { invoiceNumber: r.invoice_number },
                `bill-${r.invoice_number}`
              );
            }}
            className="inline-flex items-center gap-1.5 text-brand hover:underline font-mono text-xs font-semibold cursor-pointer transition-colors"
            title={strings.productFlow.productFlow.viewBillTitle(r.invoice_number)}
          >
            <Receipt className="w-3.5 h-3.5 text-brand shrink-0" />
            <span>{r.invoice_number}</span>
            <ExternalLink className="w-3 h-3 opacity-60 shrink-0" />
          </button>
        );
      },
    },
    {
      key: 'customer_name',
      label: strings.common.labels.customer,
      className: 'text-content-secondary',
      render: (r) => (
        <span className="inline-flex items-center gap-1.5 text-content-secondary">
          <User className="w-3 h-3 text-content-muted shrink-0" />
          <span className="truncate max-w-[160px]">
            {r.customer_name || strings.productFlow.productFlow.walkInCustomer}
          </span>
        </span>
      ),
    },
    {
      key: 'event_type',
      label: strings.productFlow.columns.eventType,
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => (
        <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-surface-secondary text-content-secondary border border-border">
          {r.event_type}
        </span>
      ),
    },
    {
      key: 'quantity',
      label: strings.productFlow.columns.soldQty,
      headerClassName: 'text-center',
      className: 'text-center font-medium financial-value',
      render: (r) => {
        const isOut = r.quantity < 0;
        return (
          <span
            className={`inline-flex items-center gap-1 font-medium ${
              isOut ? 'text-danger' : 'text-success'
            }`}
          >
            {isOut ? <ArrowDownRight className="w-3.5 h-3.5" /> : <ArrowUpRight className="w-3.5 h-3.5" />}
            <span>{Math.abs(r.quantity)}</span>
          </span>
        );
      },
    },
    {
      key: 'unit_price_paise',
      label: strings.productFlow.columns.soldUnitPrice,
      headerClassName: 'text-right',
      className: 'text-right text-content-secondary financial-value',
      render: (r) => formatPaise(r.unit_price_paise),
    },
    {
      key: 'total_paise',
      label: strings.productFlow.columns.soldTotalAmount,
      headerClassName: 'text-right',
      className: 'text-right font-medium text-content financial-value',
      render: (r) => formatPaise(r.total_paise),
    },
    {
      key: 'payment_method',
      label: strings.productFlow.columns.payment,
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => (
        <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-surface-secondary text-content-secondary border border-border">
          {r.payment_method ? r.payment_method.replace(/_/g, ' ') : strings.common.states.emptyValue}
        </span>
      ),
    },
  ];

  const handleReset = () => {
    setSelectedProductId('');
    setFromDate('');
    setToDate('');
  };

  const handleExportCsv = () =>
    exportProductFlowCsv({
      hasSelectedProduct,
      flowItems,
      selectedProduct,
      currencySymbol,
      fromDate,
      toDate,
    });

  const handleExportXlsx = () =>
    exportProductFlowXlsx({
      hasSelectedProduct,
      flowItems,
      selectedProduct,
      currencySymbol,
      fromDate,
      toDate,
    });

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas text-content overflow-hidden">
      {/* Header */}
      <div className="h-14 px-6 border-b border-border bg-surface flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-brand-subtle text-brand flex items-center justify-center border border-brand/20">
            <History className="w-4 h-4" />
          </div>
          <div>
            <h1 className="text-sm font-semibold text-content">{strings.productFlow.productFlow.title}</h1>
            <p className="text-xs text-content-secondary">
              {strings.productFlow.productFlow.subtitle}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {(selectedProductId || fromDate || toDate) && (
            <button
              onClick={handleReset}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface border border-border hover:bg-surface-secondary text-content-secondary hover:text-content text-xs font-medium transition-colors cursor-pointer"
            >
              <RotateCcw className="w-3.5 h-3.5 text-content-muted" />
              <span>{strings.common.actions.reset}</span>
            </button>
          )}
          <ExportButton
            onExportCsv={handleExportCsv}
            onExportXlsx={handleExportXlsx}
            disabled={!hasSelectedProduct || !flowItems.length}
          />

          <button
            onClick={() => refetch()}
            disabled={!hasSelectedProduct}
            className="p-1.5 rounded-lg border border-border-default text-text-muted hover:text-text-primary hover:bg-surface-secondary transition-colors disabled:opacity-40 disabled:pointer-events-none cursor-pointer"
            title={strings.productFlow.productFlow.refreshTitle}
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Control Filter Bar */}
      <div className="px-6 py-3.5 border-b border-border bg-surface flex items-center gap-4 flex-wrap text-xs">
        {/* Product Picker */}
        <div className="flex items-center gap-2 min-w-64 flex-1 max-w-md">
          <span className="text-content-secondary font-medium shrink-0">
            {strings.productFlow.productFlow.selectProduct}
          </span>
          <Select
            value={selectedProductId}
            onChange={(val) => setSelectedProductId(val)}
            placeholder={strings.productFlow.productFlow.selectProductPlaceholder}
            options={[
              { value: '', label: strings.productFlow.productFlow.selectProductPlaceholder },
              ...products.map((p) => ({
                value: String(p.id),
                label: strings.productFlow.productFlow.productOption(p.name, p.sku),
              })),
            ]}
            className="w-full"
          />
        </div>

        {/* Date Filters */}
        <div className="flex items-center gap-2">
          <span className="text-content-secondary font-medium">{strings.productFlow.filters.fromLabel}</span>
          <DatePicker
            value={fromDate}
            onChange={(val) => setFromDate(val)}
            placeholder={strings.productFlow.filters.fromPlaceholder}
          />
        </div>

        <div className="flex items-center gap-2">
          <span className="text-content-secondary font-medium">{strings.productFlow.filters.toLabel}</span>
          <DatePicker
            value={toDate}
            onChange={(val) => setToDate(val)}
            placeholder={strings.productFlow.filters.toPlaceholder}
          />
        </div>
      </div>

      {/* Main Content Area */}
      <div className="flex-1 overflow-y-auto flex flex-col">
        {hasSelectedProduct ? (
          <div className="flex-1 flex flex-col p-6 space-y-6">
            {/* Product Summary Card */}
            <ProductFlowStats
              selectedProduct={selectedProduct}
              summaryMetrics={summaryMetrics}
            />

            {/* Transaction Flow Table */}
            <div className="flex-1 overflow-hidden border border-border rounded-xl bg-surface flex flex-col">
              <div className="px-4 py-3 border-b border-border flex items-center justify-between">
                <h3 className="text-xs font-semibold text-content uppercase tracking-wider">
                  {strings.productFlow.productFlow.transactionRecords}
                </h3>
              </div>
              <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
                <DataTable
                  columns={columns}
                  persistKey="product-flow"
                  data={flowItems}
                  keyFn={(r) => r.id}
                  searchKeys={['invoice_number', 'customer_name', 'payment_method']}
                  pageSize={25}
                  emptyMessage={strings.productFlow.productFlow.emptyTable}
                  isLoading={isLoading}
                />
              </div>
            </div>
          </div>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center p-8 text-center">
            <div className="w-12 h-12 rounded-xl bg-surface-secondary border border-border flex items-center justify-center text-content-muted mb-3">
              <Package className="w-6 h-6" />
            </div>
            <h3 className="text-sm font-semibold text-content mb-1">
              {strings.productFlow.productFlow.noProductTitle}
            </h3>
            <p className="text-xs text-content-secondary max-w-sm">
              {strings.productFlow.productFlow.noProductDescription}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
