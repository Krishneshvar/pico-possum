import { exportToCsv, exportToXlsx, escapeCsv } from '../../../lib/exportUtils';
import { toast } from '../../../stores/toast.store';
import { formatDateTime } from '../../../lib/formatters';
import { strings } from '../../../lib/strings';

export async function exportProductFlowCsv({ hasSelectedProduct, flowItems, selectedProduct, currencySymbol, fromDate, toDate }) {
  if (!hasSelectedProduct || !flowItems.length) {
    toast.warning(strings.productFlow.export.selectProductWarning);
    return;
  }

  const headers = [
    strings.common.labels.product,
    strings.common.labels.dateTime,
    strings.productFlow.export.headers.invoice,
    strings.common.labels.customer,
    strings.productFlow.export.headers.eventType,
    strings.productFlow.export.headers.soldQty,
    strings.productFlow.export.headers.soldUnitPrice(currencySymbol),
    strings.productFlow.export.headers.soldTotalAmount(currencySymbol),
    strings.common.labels.paymentMethod,
  ];

  const prodLabel = selectedProduct
    ? `${selectedProduct.name}${selectedProduct.sku ? ` (${selectedProduct.sku})` : ''}`
    : strings.common.labels.product;

  const rows = flowItems.map((r) => [
    escapeCsv(prodLabel),
    escapeCsv(formatDateTime(r.created_at)),
    escapeCsv(r.invoice_number || strings.common.states.emptyValue),
    escapeCsv(r.customer_name || strings.productFlow.productFlow.walkInCustomer),
    escapeCsv(r.event_type || ''),
    escapeCsv(r.quantity || 0),
    escapeCsv(((r.unit_price_paise || 0) / 100).toFixed(2)),
    escapeCsv(((r.total_paise || 0) / 100).toFixed(2)),
    escapeCsv(r.payment_method || strings.common.states.emptyValue),
  ]);

  const safeProdName = (selectedProduct?.name || 'product').toLowerCase().replace(/[^a-z0-9]+/g, '_');
  const dateRangeLabel = fromDate && toDate
    ? `${fromDate}_to_${toDate}`
    : fromDate
      ? `from_${fromDate}`
      : toDate
        ? `to_${toDate}`
        : 'all_time';

  await exportToCsv({
    filename: `sales_history_${safeProdName}_${dateRangeLabel}.csv`,
    headers,
    rows,
    count: flowItems.length,
    entityName: strings.productFlow.export.salesRecords,
  });
}

export async function exportProductFlowXlsx({ hasSelectedProduct, flowItems, selectedProduct, currencySymbol, fromDate, toDate }) {
  if (!hasSelectedProduct || !flowItems.length) {
    toast.warning(strings.productFlow.export.selectProductWarning);
    return;
  }

  const headers = [
    strings.common.labels.product,
    strings.common.labels.dateTime,
    strings.productFlow.export.headers.invoice,
    strings.common.labels.customer,
    strings.productFlow.export.headers.eventType,
    strings.productFlow.export.headers.soldQty,
    strings.productFlow.export.headers.soldUnitPrice(currencySymbol),
    strings.productFlow.export.headers.soldTotalAmount(currencySymbol),
    strings.common.labels.paymentMethod,
  ];

  const prodLabel = selectedProduct
    ? `${selectedProduct.name}${selectedProduct.sku ? ` (${selectedProduct.sku})` : ''}`
    : strings.common.labels.product;

  const rows = flowItems.map((r) => [
    prodLabel,
    formatDateTime(r.created_at),
    r.invoice_number || strings.common.states.emptyValue,
    r.customer_name || strings.productFlow.productFlow.walkInCustomer,
    r.event_type || '',
    r.quantity || 0,
    Number(((r.unit_price_paise || 0) / 100).toFixed(2)),
    Number(((r.total_paise || 0) / 100).toFixed(2)),
    r.payment_method || strings.common.states.emptyValue,
  ]);

  const safeProdName = (selectedProduct?.name || 'product').toLowerCase().replace(/[^a-z0-9]+/g, '_');
  const dateRangeLabel = fromDate && toDate
    ? `${fromDate}_to_${toDate}`
    : fromDate
      ? `from_${fromDate}`
      : toDate
        ? `to_${toDate}`
        : 'all_time';

  await exportToXlsx({
    filename: `sales_history_${safeProdName}_${dateRangeLabel}.xlsx`,
    sheetName: strings.productFlow.export.salesSheet,
    headers,
    rows,
    count: flowItems.length,
    entityName: strings.productFlow.export.salesRecords,
  });
}
