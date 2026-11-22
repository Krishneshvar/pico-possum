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
  X,
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatCurrency, formatDateTime, getCurrencySymbol } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';
import Select from '../../../components/common/Select';
import SearchableSelect from '../../../components/common/SearchableSelect';
import DatePicker from '../../../components/common/DatePicker';
import ExportButton from '../../../components/common/ExportButton';
import RefreshButton from '../../../components/common/RefreshButton';
import { useWorkspaceStore } from '../../../stores/workspaceStore';
import { strings } from '../../../lib/strings';
import ProductFlowStats from './ProductFlowStats';
import { exportProductFlowCsv, exportProductFlowXlsx } from './productFlowExport';

export default function ProductFlowView() {
  const { openOrFocus } = useWorkspaceStore();
  const [selectedProductId, setSelectedProductId] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  const hasActiveFilters = Boolean(selectedProductId || fromDate || toDate);

  const { data: generalSettings } = useQuery({
    queryKey: ['generalSettings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });
  const currencySymbol = generalSettings?.currencySymbol || getCurrencySymbol();

  const { data: products = [] } = useQuery({
    queryKey: ['products', 'allFlowProducts'],
    queryFn: () => tauriApi.getProducts(null, null, true),
  });

  const selectedProduct = useMemo(
    () => products.find((p) => p.id === selectedProductId) || null,
    [products, selectedProductId]
  );

  const productOptions = useMemo(() => {
    return products.map((p) => {
      const isDel = p.isDeleted ?? p.is_deleted;
      const status = isDel ? 'Deleted' : p.status !== 'active' ? p.status : '';
      return {
        value: String(p.id),
        label: p.name,
        sku: p.sku || '',
        status,
      };
    });
  }, [products]);

  const hasSelectedProduct = Boolean(selectedProductId);

  const { data: flowItems = [], isLoading, refetch } = useQuery({
    queryKey: ['productFlow', selectedProductId, fromDate, toDate],
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
    const salesOnly = flowItems.filter((f) => f.quantity < 0 || (f.eventType ?? f.event_type) === 'SALE');
    const itemsToCount = salesOnly.length > 0 ? salesOnly : flowItems;
    const unitsSold = itemsToCount.reduce((acc, r) => acc + Math.abs(r.quantity || 0), 0);
    const totalRevenue = itemsToCount.reduce((acc, r) => acc + ((r.totalCents ?? r.total_cents) || 0), 0);
    const avgPrice = unitsSold > 0 ? Math.round(totalRevenue / unitsSold) : 0;

    return {
      unitsSold,
      totalRevenue,
      avgPrice,
    };
  }, [flowItems]);

  const columns = [
    {
      key: 'createdAt',
      label: strings.common.labels.dateTime,
      className: 'text-content-secondary text-xs whitespace-nowrap',
      render: (r) => formatDateTime(r.createdAt ?? r.created_at),
    },
    {
      key: 'invoiceNumber',
      label: strings.productFlow.columns.invoice,
      className: 'font-mono text-content font-medium',
      render: (r) => {
        const invNo = r.invoiceNumber ?? r.invoice_number;
        if (!invNo) return <span className="text-content-muted">{strings.common.states.emptyValue}</span>;
        return (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              openOrFocus(
                strings.productFlow.productFlow.billTabTitle(invNo),
                'bill-detail',
                { invoiceNumber: invNo },
                `bill-${invNo}`
              );
            }}
            className="inline-flex items-center gap-1.5 text-brand hover:underline font-mono text-xs font-semibold cursor-pointer transition-colors"
            title={strings.productFlow.productFlow.viewBillTitle(invNo)}
          >
            <Receipt className="w-3.5 h-3.5 text-brand shrink-0" />
            <span>{invNo}</span>
            <ExternalLink className="w-3 h-3 opacity-60 shrink-0" />
          </button>
        );
      },
    },
    {
      key: 'customerName',
      label: strings.common.labels.customer,
      className: 'text-content-secondary',
      render: (r) => {
        const custName = r.customerName ?? r.customer_name;
        return (
          <span className="inline-flex items-center gap-1.5 text-content-secondary">
            <User className="w-3 h-3 text-content-muted shrink-0" />
            <span className="truncate max-w-[160px]">
              {custName || strings.productFlow.productFlow.walkInCustomer}
            </span>
          </span>
        );
      },
    },
    {
      key: 'eventType',
      label: strings.productFlow.columns.eventType,
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => (
        <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-surface-secondary text-content-secondary border border-border">
          {r.eventType ?? r.event_type}
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
      key: 'unitPriceCents',
      label: strings.productFlow.columns.soldUnitPrice,
      headerClassName: 'text-right',
      className: 'text-right text-content-secondary financial-value',
      render: (r) => formatCurrency(r.unitPriceCents ?? r.unit_price_cents),
    },
    {
      key: 'totalCents',
      label: strings.productFlow.columns.soldTotalAmount,
      headerClassName: 'text-right',
      className: 'text-right font-medium text-content financial-value',
      render: (r) => formatCurrency(r.totalCents ?? r.total_cents),
    },
    {
      key: 'paymentMethod',
      label: strings.productFlow.columns.payment,
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => {
        const pm = r.paymentMethod ?? r.payment_method;
        return (
          <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-surface-secondary text-content-secondary border border-border">
            {pm ? pm.replace(/_/g, ' ') : strings.common.states.emptyValue}
          </span>
        );
      },
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
          <ExportButton
            onExportCsv={handleExportCsv}
            onExportXlsx={handleExportXlsx}
            disabled={!hasSelectedProduct || !flowItems.length}
          />

          <RefreshButton
            onClick={() => refetch()}
            disabled={!hasSelectedProduct}
            isLoading={isLoading}
            title={strings.productFlow.productFlow.refreshTitle}
          />
        </div>
      </div>

      {/* Control Filter Bar */}
      <div className="px-6 py-3.5 border-b border-border bg-surface flex items-center gap-4 flex-wrap text-xs">
        {/* Product Picker Searchbar with Dropdown */}
        <div className="flex items-center gap-2 min-w-80 flex-1 max-w-lg">
          <span className="text-content-secondary font-medium shrink-0">
            {strings.productFlow.productFlow.selectProduct}
          </span>
          <SearchableSelect
            value={selectedProductId}
            onChange={(val) => setSelectedProductId(val)}
            placeholder={strings.productFlow.productFlow.searchProductPlaceholder || 'Search product by name, SKU...'}
            options={productOptions}
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

        {hasActiveFilters && (
          <button
            type="button"
            onClick={handleReset}
            className="flex items-center gap-1 text-[11px] font-medium text-danger hover:underline transition-colors cursor-pointer px-1 py-0.5"
          >
            <X className="w-3 h-3" />
            <span>{strings.common.table.clearFilters}</span>
          </button>
        )}
      </div>

      {/* Main Content Area */}
      <div className="flex-1 overflow-hidden flex flex-col p-4">
        {hasSelectedProduct ? (
          <div className="flex-1 min-h-0 flex flex-col overflow-hidden border border-border rounded-xl bg-surface">
            {/* Product Summary Header */}
            <ProductFlowStats
              selectedProduct={selectedProduct}
              summaryMetrics={summaryMetrics}
            />

            {/* Transaction Flow Table */}
            <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
              <DataTable
                columns={columns}
                persistKey="product-flow"
                data={flowItems}
                keyFn={(r) => r.id}
                searchKeys={['invoiceNumber', 'customerName', 'paymentMethod']}
                pageSize={25}
                emptyMessage={strings.productFlow.productFlow.emptyTable}
                isLoading={isLoading}
              />
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
